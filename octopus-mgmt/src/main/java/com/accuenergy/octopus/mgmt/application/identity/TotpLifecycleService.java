package com.accuenergy.octopus.mgmt.application.identity;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.iam.IamAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.iam.PasswordHasher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

public final class TotpLifecycleService {
    private static final char[] RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final AccountCredentialPort accounts;
    private final PasswordVerifier passwords;
    private final PasswordHasher passwordHasher;
    private final TotpSecretEncryptor secrets;
    private final TotpEnrollmentVerifier enrollmentVerifier;
    private final TotpVerifier totpVerifier;
    private final TotpLifecycleRepository repository;
    private final AccountSessionGenerationRegistry generations;
    private final SecureRandom random;
    private final Clock clock;
    private final Duration enrollmentTtl;
    private final String issuer;

    public TotpLifecycleService(AccountCredentialPort accounts, PasswordVerifier passwords,
            PasswordHasher passwordHasher, TotpSecretEncryptor secrets,
            TotpEnrollmentVerifier enrollmentVerifier, TotpVerifier totpVerifier,
            TotpLifecycleRepository repository, AccountSessionGenerationRegistry generations,
            SecureRandom random, Clock clock, Duration enrollmentTtl, String issuer) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.passwordHasher = passwordHasher;
        this.secrets = secrets;
        this.enrollmentVerifier = enrollmentVerifier;
        this.totpVerifier = totpVerifier;
        this.repository = repository;
        this.generations = generations;
        this.random = random;
        this.clock = clock;
        this.enrollmentTtl = enrollmentTtl;
        this.issuer = issuer;
        if (enrollmentTtl.isNegative() || enrollmentTtl.isZero()) {
            throw new IllegalArgumentException("TOTP enrollment TTL must be positive");
        }
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("TOTP issuer is required");
    }

    public Enrollment startEnrollment(AuthenticatedPrincipal principal, StartEnrollment command) {
        AccountCredential account = requireActiveAccount(principal.accountId());
        char[] currentPassword = command.currentPassword();
        byte[] rawSecret = new byte[20];
        try {
            if (!passwords.matches(currentPassword, account.passwordHash())) throw invalidCredentials();
            if (repository.findActive(account.accountId()).isPresent()) {
                throw new IllegalStateException("TOTP is already active");
            }
            random.nextBytes(rawSecret);
            String encodedSecret = base32(rawSecret);
            var now = clock.instant();
            var expiresAt = now.plus(enrollmentTtl);
            UUID factorId = repository.createPending(account.accountId(), secrets.encrypt(rawSecret),
                    "local-aes-gcm-v1", principal.accountId(), principal.sessionId(), expiresAt, now);
            String label = issuer + ":" + account.accountId();
            String uri = "otpauth://totp/" + encode(label) + "?secret=" + encodedSecret
                    + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
            return new Enrollment(factorId, encodedSecret, uri, expiresAt);
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(rawSecret, (byte) 0);
            command.clearPassword();
        }
    }

    public Activation activate(AuthenticatedPrincipal principal, UUID factorId, String code) {
        var now = clock.instant();
        AccountCredential account = requireActiveAccount(principal.accountId());
        TotpLifecycleRepository.Factor factor = repository.findPending(account.accountId(), factorId, now)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or expired TOTP enrollment"));
        OptionalLong acceptedCounter = enrollmentVerifier.verifyCandidate(factor.encryptedSecret(), code, now);
        if (acceptedCounter.isEmpty()) throw invalidCredentials();

        List<char[]> recoveryCodes = generateRecoveryCodes(10);
        try {
            List<String> hashes = recoveryCodes.stream().map(passwordHasher::hash).toList();
            long nextGeneration = generations.revokeAll(account.accountId(), account.sessionGeneration());
            repository.activate(factor, acceptedCounter.getAsLong(), hashes, nextGeneration,
                    principal.accountId(), principal.sessionId(), now);
            return new Activation(factor.factorId(), recoveryCodes.stream()
                    .map(String::new).toList(), nextGeneration);
        } finally {
            recoveryCodes.forEach(value -> Arrays.fill(value, '\0'));
        }
    }

    public Change remove(AuthenticatedPrincipal principal, RemoveTotp command) {
        AccountCredential account = requireActiveAccount(principal.accountId());
        TotpLifecycleRepository.Factor factor = repository.findActive(account.accountId())
                .orElseThrow(() -> new IllegalArgumentException("TOTP is not active"));
        char[] currentPassword = command.currentPassword();
        try {
            if (!passwords.matches(currentPassword, account.passwordHash())
                    || !totpVerifier.verify(factor.factorId(), factor.encryptedSecret(),
                            command.totpCode(), clock.instant())) {
                throw invalidCredentials();
            }
            long nextGeneration = generations.revokeAll(account.accountId(), account.sessionGeneration());
            repository.revoke(account.accountId(), nextGeneration, principal.accountId(), principal.sessionId(),
                    "TOTP_REMOVED", "Self-service TOTP removal", clock.instant());
            return new Change(account.accountId(), nextGeneration);
        } finally {
            Arrays.fill(currentPassword, '\0');
            command.clearPassword();
        }
    }

    public Change platformReset(AuthenticatedPrincipal principal, UUID accountId, String reason) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new IamAccessDeniedException();
        }
        String normalizedReason = normalizeReason(reason);
        AccountCredential account = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account"));
        if (repository.findCurrent(accountId).isEmpty()) {
            return new Change(accountId, account.sessionGeneration());
        }
        long nextGeneration = generations.revokeAll(accountId, account.sessionGeneration());
        repository.revoke(accountId, nextGeneration, principal.accountId(), principal.sessionId(),
                "TOTP_RESET_BY_PLATFORM", normalizedReason, clock.instant());
        return new Change(accountId, nextGeneration);
    }

    private AccountCredential requireActiveAccount(UUID accountId) {
        AccountCredential account = accounts.findById(accountId).orElseThrow(TotpLifecycleService::invalidCredentials);
        if (account.status() != AccountCredential.Status.ACTIVE) throw invalidCredentials();
        return account;
    }

    private List<char[]> generateRecoveryCodes(int count) {
        List<char[]> codes = new ArrayList<>(count);
        java.util.Set<String> unique = new java.util.HashSet<>(count);
        while (codes.size() < count) {
            char[] code = new char[14];
            for (int index = 0; index < code.length; index++) {
                if (index == 4 || index == 9) code[index] = '-';
                else code[index] = RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)];
            }
            if (unique.add(new String(code))) codes.add(code);
            else Arrays.fill(code, '\0');
        }
        return codes;
    }

    private static String base32(byte[] value) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder result = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte current : value) {
            buffer = (buffer << 8) | (current & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(alphabet[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) result.append(alphabet[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Reset reason is invalid");
        }
        return reason.strip();
    }

    private static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("Invalid credentials");
    }

    public record StartEnrollment(char[] currentPassword) {
        public StartEnrollment { currentPassword = currentPassword.clone(); }
        @Override public char[] currentPassword() { return currentPassword.clone(); }
        public void clearPassword() { Arrays.fill(currentPassword, '\0'); }
    }
    public record RemoveTotp(char[] currentPassword, String totpCode) {
        public RemoveTotp { currentPassword = currentPassword.clone(); }
        @Override public char[] currentPassword() { return currentPassword.clone(); }
        public void clearPassword() { Arrays.fill(currentPassword, '\0'); }
    }
    public record Enrollment(UUID factorId, String secret, String otpauthUri, java.time.Instant expiresAt) { }
    public record Activation(UUID factorId, List<String> recoveryCodes, long sessionGeneration) {
        public Activation { recoveryCodes = List.copyOf(recoveryCodes); }
    }
    public record Change(UUID accountId, long sessionGeneration) { }
}

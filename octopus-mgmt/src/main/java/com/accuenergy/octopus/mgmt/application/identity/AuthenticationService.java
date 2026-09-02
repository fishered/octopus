package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

public final class AuthenticationService {
    private final AccountCredentialPort accounts;
    private final PasswordVerifier passwords;
    private final TotpVerifier totp;
    private final RecoveryCodeVerifier recoveryCodes;
    private final MutableSessionRegistry sessions;
    private final TokenIssuer tokens;
    private final AuthorizationSnapshotPort authorizations;
    private final RefreshTokenVerifier refreshTokens;
    private final AccountSessionGenerationRegistry accountGenerations;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final String dummyPasswordHash;

    public AuthenticationService(AccountCredentialPort accounts, PasswordVerifier passwords, TotpVerifier totp,
                                 RecoveryCodeVerifier recoveryCodes,
                                 MutableSessionRegistry sessions, TokenIssuer tokens,
                                 AuthorizationSnapshotPort authorizations,
                                 RefreshTokenVerifier refreshTokens,
                                 AccountSessionGenerationRegistry accountGenerations, Clock clock,
                                 Duration idleTimeout, Duration absoluteTimeout, String dummyPasswordHash) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.sessions = sessions;
        this.tokens = tokens;
        this.authorizations = authorizations;
        this.refreshTokens = refreshTokens;
        this.accountGenerations = accountGenerations;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        this.absoluteTimeout = absoluteTimeout;
        this.dummyPasswordHash = dummyPasswordHash;
    }

    public TokenIssuer.TokenPair login(LoginCommand command) {
        String login = normalize(command.login());
        char[] password = command.password();
        try {
            AccountCredential account = accounts.findByLogin(login).orElse(null);
            boolean passwordMatches = passwords.matches(password,
                    account == null ? dummyPasswordHash : account.passwordHash());
            if (account == null || account.status() != AccountCredential.Status.ACTIVE || !passwordMatches) {
                accounts.recordFailure(login);
                throw genericFailure();
            }
            if (account.totpFactor().isPresent()) {
                AccountCredential.TotpFactor factor = account.totpFactor().orElseThrow();
                boolean hasTotp = command.totpCode().isPresent();
                boolean hasRecovery = command.recoveryCode().isPresent();
                boolean verified = hasTotp != hasRecovery && (hasTotp
                        ? totp.verify(factor.factorId(), factor.encryptedSecret(),
                                command.totpCode().orElseThrow(), clock.instant())
                        : recoveryCodes.consume(account.accountId(), factor.factorId(),
                                command.recoveryCode().orElseThrow(), clock.instant()));
                if (!verified) {
                    accounts.recordFailure(login);
                    throw genericFailure();
                }
            }
            AuthorizationSnapshot authorization = authorizations.resolve(account.accountId(), command.tenantId())
                    .orElseThrow(AuthenticationService::genericFailure);
            accounts.clearFailures(login);
            long accountGeneration = accountGenerations.current(account.accountId(), account.sessionGeneration());
            SessionSnapshot session = sessions.create(account.accountId(), authorization.tenantId(),
                    accountGeneration, authorization.generation(),
                    clock.instant(), idleTimeout, absoluteTimeout);
            return tokens.issue(session, authorization, clock.instant());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public TokenIssuer.TokenPair refresh(java.util.UUID sessionId, long generation, String refreshToken) {
        SessionSnapshot current = sessions.find(sessionId).orElseThrow(AuthenticationService::genericFailure);
        if (!refreshTokens.matches(sessionId, generation, refreshToken)) throw genericFailure();
        AccountCredential account = accounts.findById(current.accountId())
                .orElseThrow(AuthenticationService::genericFailure);
        long currentAccountGeneration = accountGenerations.current(
                account.accountId(), account.sessionGeneration());
        if (account.status() != AccountCredential.Status.ACTIVE
                || currentAccountGeneration != current.accountGeneration()) {
            throw genericFailure();
        }
        AuthorizationSnapshot authorization = authorizations.resolve(current.accountId(), current.tenantId())
                .orElseThrow(AuthenticationService::genericFailure);
        SessionSnapshot rotated = sessions.rotate(sessionId, generation, authorization.generation(),
                clock.instant(), idleTimeout);
        return tokens.rotate(rotated, authorization, clock.instant());
    }

    public void logout(java.util.UUID sessionId) {
        sessions.revoke(sessionId, "logout");
    }

    private static String normalize(String login) {
        if (login == null || login.isBlank()) throw genericFailure();
        return login.strip().toLowerCase(Locale.ROOT);
    }

    private static AuthenticationFailedException genericFailure() {
        return new AuthenticationFailedException("Invalid credentials");
    }
}

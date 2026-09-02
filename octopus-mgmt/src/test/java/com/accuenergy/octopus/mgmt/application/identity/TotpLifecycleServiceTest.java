package com.accuenergy.octopus.mgmt.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.iam.IamAccessDeniedException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private final UUID accountId = UUID.randomUUID();
    private final MemoryAccounts accounts = new MemoryAccounts(accountId);
    private final MemoryRepository repository = new MemoryRepository();
    private final MemoryGenerations generations = new MemoryGenerations();
    private final TotpLifecycleService service = new TotpLifecycleService(accounts,
            (presented, hash) -> hash.equals("current-hash")
                    && java.util.Arrays.equals(presented, "current-password".toCharArray()),
            password -> "argon2id:" + new String(password),
            secret -> "encrypted-envelope",
            (encrypted, code, now) -> "123456".equals(code) ? OptionalLong.of(42) : OptionalLong.empty(),
            (factor, encrypted, code, now) -> "654321".equals(code),
            repository, generations, new DeterministicSecureRandom(),
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10), "Octopus");

    @Test
    void enrollmentRequiresPasswordAndReturnsSecretOnlyForPendingSetup() {
        var command = new TotpLifecycleService.StartEnrollment("current-password".toCharArray());

        TotpLifecycleService.Enrollment enrollment = service.startEnrollment(platform(accountId), command);

        assertEquals(repository.factor.factorId(), enrollment.factorId());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), enrollment.expiresAt());
        assertTrue(enrollment.secret().matches("[A-Z2-7]{32}"));
        assertTrue(enrollment.otpauthUri().startsWith("otpauth://totp/Octopus%3A"));
        assertTrue(enrollment.otpauthUri().contains("issuer=Octopus"));
        assertEquals("encrypted-envelope", repository.factor.encryptedSecret());
        assertNotEquals(enrollment.secret(), repository.factor.encryptedSecret());
        assertTrue(new String(command.currentPassword()).chars().allMatch(value -> value == 0));

        assertThrows(AuthenticationFailedException.class, () -> service.startEnrollment(platform(accountId),
                new TotpLifecycleService.StartEnrollment("incorrect-password".toCharArray())));
    }

    @Test
    void activationVerifiesCandidateCreatesOneTimeRecoveryCodesAndRevokesSessions() {
        service.startEnrollment(platform(accountId),
                new TotpLifecycleService.StartEnrollment("current-password".toCharArray()));

        TotpLifecycleService.Activation activation = service.activate(
                platform(accountId), repository.factor.factorId(), "123456");

        assertEquals(42, repository.acceptedCounter);
        assertEquals(TotpLifecycleRepository.Status.ACTIVE, repository.factor.status());
        assertEquals(10, activation.recoveryCodes().size());
        assertEquals(10, new HashSet<>(activation.recoveryCodes()).size());
        assertTrue(activation.recoveryCodes().stream().allMatch(code ->
                code.matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}")));
        assertEquals(10, repository.recoveryHashes.size());
        assertTrue(repository.recoveryHashes.stream().allMatch(hash -> hash.startsWith("argon2id:")));
        assertEquals(5, activation.sessionGeneration());
        assertEquals(5, repository.persistedGeneration);
    }

    @Test
    void selfRemovalRequiresPasswordAndCurrentTotpThenRevokesEverySession() {
        repository.factor = activeFactor(accountId);
        var command = new TotpLifecycleService.RemoveTotp(
                "current-password".toCharArray(), "654321");

        TotpLifecycleService.Change change = service.remove(platform(accountId), command);

        assertEquals(5, change.sessionGeneration());
        assertEquals("TOTP_REMOVED", repository.eventType);
        assertEquals("Self-service TOTP removal", repository.reason);
        assertTrue(repository.revoked);
        assertTrue(new String(command.currentPassword()).chars().allMatch(value -> value == 0));
    }

    @Test
    void platformCanResetFactorButTenantOperatorCannot() {
        repository.factor = activeFactor(accountId);

        TotpLifecycleService.Change change = service.platformReset(
                platform(UUID.randomUUID()), accountId, "Identity recovery verified");

        assertEquals(5, change.sessionGeneration());
        assertEquals("TOTP_RESET_BY_PLATFORM", repository.eventType);
        assertEquals("Identity recovery verified", repository.reason);
        assertThrows(IamAccessDeniedException.class, () -> service.platformReset(
                operator(), accountId, "Unauthorized"));
    }

    private static TotpLifecycleRepository.Factor activeFactor(UUID accountId) {
        return new TotpLifecycleRepository.Factor(UUID.randomUUID(), accountId, "encrypted-envelope",
                TotpLifecycleRepository.Status.ACTIVE, Optional.empty());
    }

    private static AuthenticatedPrincipal platform(UUID accountId) {
        return new AuthenticatedPrincipal(accountId, UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN, Optional.empty(),
                Set.of("platform:all"), Set.of(), Set.of());
    }

    private static AuthenticatedPrincipal operator() {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(
                        new com.accuenergy.octopus.common.tenant.TenantId(UUID.randomUUID())),
                Set.of(), Set.of("/root"), Set.of());
    }

    private static final class MemoryAccounts implements AccountCredentialPort {
        private final AccountCredential account;
        private MemoryAccounts(UUID accountId) {
            account = new AccountCredential(accountId, "current-hash", 4,
                    AccountCredential.Status.ACTIVE, Optional.empty());
        }
        public Optional<AccountCredential> findByLogin(String login) { return Optional.of(account); }
        public Optional<AccountCredential> findById(UUID id) {
            return account.accountId().equals(id) ? Optional.of(account) : Optional.empty();
        }
        public void recordFailure(String login) { }
        public void clearFailures(String login) { }
        public int advanceSessionGeneration(UUID id, long generation, Instant now) { return 1; }
    }

    private static final class MemoryGenerations implements AccountSessionGenerationRegistry {
        private long value;
        public long current(UUID accountId, long baseline) {
            value = Math.max(value, baseline);
            return value;
        }
        public long revokeAll(UUID accountId, long baseline) {
            value = Math.max(value, baseline) + 1;
            return value;
        }
    }

    private static final class MemoryRepository implements TotpLifecycleRepository {
        private Factor factor;
        private long acceptedCounter;
        private List<String> recoveryHashes = List.of();
        private long persistedGeneration;
        private String eventType;
        private String reason;
        private boolean revoked;

        public Optional<Factor> findActive(UUID accountId) {
            return factor != null && factor.accountId().equals(accountId)
                    && factor.status() == Status.ACTIVE ? Optional.of(factor) : Optional.empty();
        }
        public Optional<Factor> findPending(UUID accountId, UUID factorId, Instant now) {
            return factor != null && factor.accountId().equals(accountId) && factor.factorId().equals(factorId)
                    && factor.status() == Status.PENDING && factor.expiresAt().orElseThrow().isAfter(now)
                    ? Optional.of(factor) : Optional.empty();
        }
        public Optional<Factor> findCurrent(UUID accountId) {
            return factor != null && factor.accountId().equals(accountId) && !revoked
                    ? Optional.of(factor) : Optional.empty();
        }
        public UUID createPending(UUID accountId, String encryptedSecret, String keyVersion,
                                  UUID actorAccountId, UUID sessionId, Instant expiresAt, Instant now) {
            UUID id = UUID.randomUUID();
            factor = new Factor(id, accountId, encryptedSecret, Status.PENDING, Optional.of(expiresAt));
            return id;
        }
        public void activate(Factor factor, long acceptedCounter, List<String> recoveryCodeHashes,
                             long sessionGeneration, UUID actorAccountId, UUID sessionId, Instant now) {
            this.factor = new Factor(factor.factorId(), factor.accountId(), factor.encryptedSecret(),
                    Status.ACTIVE, Optional.empty());
            this.acceptedCounter = acceptedCounter;
            this.recoveryHashes = List.copyOf(recoveryCodeHashes);
            this.persistedGeneration = sessionGeneration;
            this.eventType = "TOTP_ACTIVATED";
        }
        public void revoke(UUID accountId, long sessionGeneration, UUID actorAccountId, UUID sessionId,
                           String eventType, String reason, Instant now) {
            this.persistedGeneration = sessionGeneration;
            this.eventType = eventType;
            this.reason = reason;
            this.revoked = true;
        }
    }

    private static final class DeterministicSecureRandom extends SecureRandom {
        private int state = 0x13579BDF;
        @Override public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
        }
        @Override public int nextInt(int bound) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            return Math.floorMod(state, bound);
        }
    }
}

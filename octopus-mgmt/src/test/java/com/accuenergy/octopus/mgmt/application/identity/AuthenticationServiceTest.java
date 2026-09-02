package com.accuenergy.octopus.mgmt.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    @Test
    void unknownLoginStillRunsDummyPasswordVerificationAndDoesNotCreateSession() {
        AtomicInteger passwordChecks = new AtomicInteger();
        AtomicInteger sessionCreates = new AtomicInteger();
        PasswordVerifier passwords = (presented, hash) -> {
            passwordChecks.incrementAndGet();
            assertEquals("dummy-hash", hash);
            return false;
        };
        var service = new AuthenticationService(new EmptyAccounts(), passwords,
                (account, factor, code, now) -> false,
                (factor, secret, code, now) -> false, new EmptySessions(sessionCreates),
                new NoTokens(), (account, tenant) -> Optional.empty(),
                (session, generation, token) -> false,
                new AccountSessionGenerationRegistry() {
                    public long current(UUID accountId, long baseline) { return baseline; }
                    public long revokeAll(UUID accountId, long baseline) { return baseline + 1; }
                }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(30),
                Duration.ofHours(8), "dummy-hash");

        assertThrows(AuthenticationFailedException.class, () -> service.login(
                new LoginCommand("missing@example.com", "secret".toCharArray(), Optional.empty(),
                        Optional.empty(), Optional.empty())));
        assertEquals(1, passwordChecks.get());
        assertEquals(0, sessionCreates.get());
    }

    @Test
    void suspendedAndDisabledAccountsCannotRefreshExistingSessions() {
        for (AccountCredential.Status status : new AccountCredential.Status[] {
                AccountCredential.Status.SUSPENDED, AccountCredential.Status.DISABLED }) {
            UUID accountId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            AtomicInteger rotations = new AtomicInteger();
            SessionSnapshot session = new SessionSnapshot(sessionId, accountId, Optional.empty(),
                    3, 9, 1, SessionSnapshot.Status.ACTIVE,
                    Instant.EPOCH.plusSeconds(3600), Instant.EPOCH.plusSeconds(7200));
            AccountCredentialPort accounts = new AccountCredentialPort() {
                public Optional<AccountCredential> findByLogin(String login) { return Optional.empty(); }
                public Optional<AccountCredential> findById(UUID id) {
                    return Optional.of(new AccountCredential(accountId, "hash", 9, status, Optional.empty()));
                }
                public void recordFailure(String login) { }
                public void clearFailures(String login) { }
                public int advanceSessionGeneration(UUID id, long generation, Instant now) { return 1; }
            };
            MutableSessionRegistry sessions = new MutableSessionRegistry() {
                public Optional<SessionSnapshot> find(UUID id) { return Optional.of(session); }
                public void revoke(UUID id, String reason) { }
                public SessionSnapshot create(UUID id, Optional<UUID> tenantId, long accountGeneration,
                        long authorizationGeneration, Instant now, Duration idle, Duration absolute) {
                    throw new AssertionError("must not create");
                }
                public SessionSnapshot rotate(UUID id, long expected, long authorizationGeneration,
                        Instant now, Duration idle) {
                    rotations.incrementAndGet();
                    throw new AssertionError("must not rotate");
                }
            };
            AuthenticationService service = new AuthenticationService(accounts, (password, hash) -> false,
                    (factor, secret, code, now) -> false,
                    (id, factor, code, now) -> false, sessions, new NoTokens(),
                    (id, tenant) -> Optional.empty(), (id, refreshGeneration, token) -> true,
                    new AccountSessionGenerationRegistry() {
                        public long current(UUID id, long baseline) { return baseline; }
                        public long revokeAll(UUID id, long baseline) { return baseline + 1; }
                    }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(30),
                    Duration.ofHours(8), "dummy-hash");

            assertThrows(AuthenticationFailedException.class,
                    () -> service.refresh(sessionId, 3, "valid-refresh-token"));
            assertEquals(0, rotations.get());
        }
    }

    @Test
    void activeTotpAccountCanUseOneRecoveryCodeButCannotSubmitBothSecondFactors() {
        UUID accountId = UUID.randomUUID();
        UUID factorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger totpChecks = new AtomicInteger();
        AtomicInteger recoveryChecks = new AtomicInteger();
        AccountCredential credential = new AccountCredential(accountId, "hash", 2,
                AccountCredential.Status.ACTIVE, Optional.of(
                        new AccountCredential.TotpFactor(factorId, "encrypted")));
        AccountCredentialPort accounts = new AccountCredentialPort() {
            public Optional<AccountCredential> findByLogin(String login) { return Optional.of(credential); }
            public Optional<AccountCredential> findById(UUID id) { return Optional.of(credential); }
            public void recordFailure(String login) { }
            public void clearFailures(String login) { }
            public int advanceSessionGeneration(UUID id, long generation, Instant now) { return 1; }
        };
        MutableSessionRegistry sessions = new MutableSessionRegistry() {
            public Optional<SessionSnapshot> find(UUID id) { return Optional.empty(); }
            public void revoke(UUID id, String reason) { }
            public SessionSnapshot create(UUID id, Optional<UUID> tenantId, long accountGeneration,
                    long authorizationGeneration, Instant now, Duration idle, Duration absolute) {
                return new SessionSnapshot(sessionId, id, tenantId, 0, accountGeneration,
                        authorizationGeneration, SessionSnapshot.Status.ACTIVE,
                        now.plus(idle), now.plus(absolute));
            }
            public SessionSnapshot rotate(UUID id, long expected, long authorizationGeneration,
                    Instant now, Duration idle) { throw new AssertionError("must not rotate"); }
        };
        TokenIssuer tokens = new TokenIssuer() {
            public TokenPair issue(SessionSnapshot session, AuthorizationSnapshot auth, Instant now) {
                return new TokenPair("access", "refresh", session.sessionId(), 0,
                        now.plusSeconds(300), session.absoluteExpiresAt());
            }
            public TokenPair rotate(SessionSnapshot session, AuthorizationSnapshot auth, Instant now) {
                throw new AssertionError("must not rotate");
            }
        };
        AuthorizationSnapshot authorization = new AuthorizationSnapshot(
                com.accuenergy.octopus.common.security.AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN,
                Optional.empty(), 0, Set.of("platform:all"), Set.of(), Set.of());
        AuthenticationService service = new AuthenticationService(accounts, (password, hash) -> true,
                (factor, encrypted, code, now) -> { totpChecks.incrementAndGet(); return false; },
                (account, factor, code, now) -> {
                    recoveryChecks.incrementAndGet();
                    return "RECOVERY-CODE".equals(code);
                }, sessions, tokens, (id, tenant) -> Optional.of(authorization),
                (id, refreshGeneration, token) -> false,
                new AccountSessionGenerationRegistry() {
                    public long current(UUID id, long baseline) { return baseline; }
                    public long revokeAll(UUID id, long baseline) { return baseline + 1; }
                }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(30),
                Duration.ofHours(8), "dummy-hash");

        TokenIssuer.TokenPair pair = service.login(new LoginCommand("user", "password".toCharArray(),
                Optional.empty(), Optional.of("RECOVERY-CODE"), Optional.empty()));
        assertEquals(sessionId, pair.sessionId());
        assertEquals(0, totpChecks.get());
        assertEquals(1, recoveryChecks.get());

        assertThrows(AuthenticationFailedException.class, () -> service.login(new LoginCommand(
                "user", "password".toCharArray(), Optional.of("123456"),
                Optional.of("RECOVERY-CODE"), Optional.empty())));
        assertEquals(0, totpChecks.get());
        assertEquals(1, recoveryChecks.get());
    }

    private static final class EmptyAccounts implements AccountCredentialPort {
        public Optional<AccountCredential> findByLogin(String login) { return Optional.empty(); }
        public Optional<AccountCredential> findById(UUID id) { return Optional.empty(); }
        public void recordFailure(String login) { }
        public void clearFailures(String login) { }
        public int advanceSessionGeneration(UUID accountId, long generation, Instant now) { return 1; }
    }

    private static final class EmptySessions implements MutableSessionRegistry {
        private final AtomicInteger creates;
        private EmptySessions(AtomicInteger creates) { this.creates = creates; }
        public Optional<SessionSnapshot> find(UUID id) { return Optional.empty(); }
        public void revoke(UUID id, String reason) { }
        public SessionSnapshot create(UUID accountId, Optional<UUID> tenantId, long accountGeneration,
                long authorizationGeneration, Instant now, Duration idle, Duration absolute) {
            creates.incrementAndGet(); throw new AssertionError("must not create");
        }
        public SessionSnapshot rotate(UUID id, long expected, long authorizationGeneration,
                Instant now, Duration idle) { throw new AssertionError("must not rotate"); }
    }

    private static final class NoTokens implements TokenIssuer {
        public TokenPair issue(SessionSnapshot session, AuthorizationSnapshot auth, Instant now) {
            throw new AssertionError("must not issue");
        }
        public TokenPair rotate(SessionSnapshot session, AuthorizationSnapshot auth, Instant now) {
            throw new AssertionError("must not rotate");
        }
    }
}

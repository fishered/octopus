package com.accuenergy.octopus.mgmt.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForceLogoutServiceTest {
    @Test
    void persistsTheNewAccountGenerationAfterRedisRevocation() {
        UUID accountId = UUID.randomUUID();
        AccountCredential account = new AccountCredential(accountId, "hash", 4,
                AccountCredential.Status.ACTIVE, Optional.empty());
        long[] persisted = {0};
        ForceLogoutService service = new ForceLogoutService(
                new AccountCredentialPort() {
                    public Optional<AccountCredential> findByLogin(String login) { return Optional.of(account); }
                    public Optional<AccountCredential> findById(UUID id) { return Optional.of(account); }
                    public void recordFailure(String login) { }
                    public void clearFailures(String login) { }
                    public int advanceSessionGeneration(UUID id, long generation, Instant now) {
                        persisted[0] = generation; return 1;
                    }
                },
                new AccountSessionGenerationRegistry() {
                    public long current(UUID id, long baseline) { return baseline; }
                    public long revokeAll(UUID id, long baseline) { return baseline + 1; }
                }, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertEquals(5, service.revokeAll(accountId));
        assertEquals(5, persisted[0]);
    }
}

package com.accuenergy.octopus.mgmt.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthSessionTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void renewalRotatesGenerationAndDoesNotExtendAbsoluteLifetime() {
        var session = AuthSession.start(UUID.randomUUID(), UUID.randomUUID(), now,
                Duration.ofMinutes(30), Duration.ofHours(8));
        assertEquals(1, session.renew(now.plus(Duration.ofMinutes(20)), Duration.ofHours(12), 0));
        assertEquals(now.plus(Duration.ofHours(8)), session.idleExpiresAt());
    }

    @Test
    void refreshReuseRevokesWholeSession() {
        var session = AuthSession.start(UUID.randomUUID(), UUID.randomUUID(), now,
                Duration.ofMinutes(30), Duration.ofHours(8));
        session.renew(now.plusSeconds(1), Duration.ofMinutes(30), 0);
        assertThrows(RefreshTokenReuseException.class,
                () -> session.renew(now.plusSeconds(2), Duration.ofMinutes(30), 0));
        assertEquals(AuthSession.Status.REVOKED, session.status());
        assertFalse(session.isUsableAt(now.plusSeconds(3)));
    }

    @Test
    void forcedLogoutImmediatelyMakesSessionUnusable() {
        var session = AuthSession.start(UUID.randomUUID(), UUID.randomUUID(), now,
                Duration.ofMinutes(30), Duration.ofHours(8));
        assertTrue(session.isUsableAt(now.plusSeconds(1)));
        session.revoke();
        assertFalse(session.isUsableAt(now.plusSeconds(2)));
    }
}

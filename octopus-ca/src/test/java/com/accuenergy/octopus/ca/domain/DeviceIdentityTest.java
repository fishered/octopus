package com.accuenergy.octopus.ca.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceIdentityTest {
    @Test
    void followsManufactureClaimIssueAndRevokeLifecycle() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = DeviceIdentity.manufacture(UUID.randomUUID(), "SERIAL-1", "Octopus",
                "MODEL-1", "BATCH-1", "sha256:fingerprint", now);
        identity.enableBootstrap(now);
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        identity.claim(tenantId, deviceId, now);
        identity.activate("CERT-1", now.plusSeconds(3600), now);
        identity.revoke(now);

        assertEquals(DeviceIdentity.Status.REVOKED, identity.status());
        assertEquals(tenantId, identity.tenantId().orElseThrow());
        assertEquals(deviceId, identity.deviceId().orElseThrow());
    }

    @Test
    void cannotClaimIdentityTwiceOrIssueExpiredCertificate() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var identity = DeviceIdentity.manufacture(UUID.randomUUID(), "SERIAL-2", "Octopus",
                "MODEL-1", "BATCH-1", "sha256:fingerprint", now);
        identity.enableBootstrap(now);
        identity.claim(UUID.randomUUID(), UUID.randomUUID(), now);
        assertThrows(IllegalStateException.class,
                () -> identity.claim(UUID.randomUUID(), UUID.randomUUID(), now));
        assertThrows(IllegalArgumentException.class, () -> identity.activate("CERT-2", now, now));
    }
}

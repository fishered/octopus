package com.accuenergy.octopus.mgmt.domain.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void facilityRequiresIanaTimeZone() {
        assertThrows(IllegalArgumentException.class, () -> Facility.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "site", "Site", "SITE", "UTC+8", null, NOW));
        Facility facility = Facility.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "site", "Site", "SITE", "Asia/Shanghai", null, NOW);
        assertEquals("Asia/Shanghai", facility.zoneId().orElseThrow());
    }

    @Test
    void delayedConnectivityEventCannotMoveLastSeenBackwards() {
        DeviceActual actual = DeviceActual.commission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "serial-1", "Octopus", "1.0.0", null, NOW);
        assertTrue(actual.recordConnectivity(DeviceActual.ConnectivityStatus.ONLINE,
                NOW.plusSeconds(60), NOW.plusSeconds(60)));
        assertFalse(actual.recordConnectivity(DeviceActual.ConnectivityStatus.OFFLINE,
                NOW.plusSeconds(30), NOW.plusSeconds(70)));
        assertEquals(DeviceActual.ConnectivityStatus.ONLINE, actual.connectivityStatus());
    }
}

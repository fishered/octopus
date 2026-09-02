package com.accuenergy.octopus.mgmt.domain.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceShadowTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void appliesOnlyNewerVersionsAndVersionsProjectionOnce() {
        DeviceShadow shadow = DeviceShadow.from(event(3, "{\"value\":3}"));

        assertFalse(shadow.apply(event(2, "{\"value\":2}")));
        assertTrue(shadow.apply(event(4, "{\"value\":4}")));
        assertFalse(shadow.apply(event(4, "{\"value\":4}")));

        assertEquals(4, shadow.shadowVersion());
        assertEquals(1, shadow.projectionVersion());
    }

    @Test
    void rejectsDifferentStateForSameDeviceVersion() {
        DeviceShadow shadow = DeviceShadow.from(event(3, "{\"value\":3}"));
        assertThrows(IllegalStateException.class, () -> shadow.apply(event(3, "{\"value\":9}")));
    }

    private DeviceShadowReported event(long version, String state) {
        UUID tenantId = IDs.tenant;
        UUID deviceId = IDs.device;
        return new DeviceShadowReported(1, UUID.randomUUID(), tenantId, deviceId, version, state,
                NOW.plusSeconds(version), NOW.plusSeconds(version));
    }

    private static final class IDs {
        private static final UUID tenant = UUID.randomUUID();
        private static final UUID device = UUID.randomUUID();
    }
}

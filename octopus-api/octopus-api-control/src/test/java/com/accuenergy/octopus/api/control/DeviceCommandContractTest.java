package com.accuenergy.octopus.api.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandContractTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void requestedEventDefensivelyCopiesPayload() {
        byte[] source = {1, 2, 3};
        DeviceCommandRequested event = new DeviceCommandRequested(1, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "request-1", "reboot",
                source, NOW, NOW.plusSeconds(30));

        source[0] = 9;
        byte[] returned = event.payload();
        returned[1] = 8;

        assertArrayEquals(new byte[]{1, 2, 3}, event.payload());
        assertEquals(event.tenantId() + ":" + event.deviceId(), event.orderingKey());
    }

    @Test
    void envelopeRejectsInvalidDeadlineAndOversizedPayload() {
        UUID commandId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new DeviceCommandEnvelope(1, commandId,
                tenantId, deviceId, "reboot", new byte[0], NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCommandEnvelope(1, commandId,
                tenantId, deviceId, "reboot", new byte[65_537], NOW, NOW.plusSeconds(1)));
    }

    @Test
    void presenceRequiresTimesConsistentWithStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new DevicePresenceSnapshot(tenantId, deviceId,
                DevicePresenceSnapshot.Status.ONLINE, NOW, null));
        assertEquals(DevicePresenceSnapshot.Status.NEVER_SEEN,
                new DevicePresenceSnapshot(tenantId, deviceId, DevicePresenceSnapshot.Status.NEVER_SEEN,
                        null, null).status());
    }

    @Test
    void shadowContractRejectsOversizedState() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceShadowReported(1, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, "x".repeat(262_145), NOW, NOW));
    }
}

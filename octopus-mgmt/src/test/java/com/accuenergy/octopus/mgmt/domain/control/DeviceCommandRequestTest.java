package com.accuenergy.octopus.mgmt.domain.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandRequestTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DIGEST = "0".repeat(64);

    @Test
    void projectsLifecycleAndIgnoresDuplicateOrStaleEvents() {
        DeviceCommandRequest request = request();

        assertTrue(request.apply(event(request, DeviceCommandStatusChanged.Status.DISPATCHED,
                NOW.plusSeconds(1), null)));
        assertFalse(request.apply(event(request, DeviceCommandStatusChanged.Status.DISPATCHED,
                NOW.plusSeconds(2), null)));
        assertFalse(request.apply(event(request, DeviceCommandStatusChanged.Status.ACCEPTED,
                NOW, null)));
        assertTrue(request.apply(event(request, DeviceCommandStatusChanged.Status.ACKNOWLEDGED,
                NOW.plusSeconds(2), null)));
        assertTrue(request.apply(event(request, DeviceCommandStatusChanged.Status.SUCCEEDED,
                NOW.plusSeconds(3), null)));

        assertEquals(DeviceCommandStatusChanged.Status.SUCCEEDED, request.status());
        assertEquals(3, request.version());
        assertEquals(NOW.plusSeconds(1), request.dispatchedAt().orElseThrow());
        assertEquals(NOW.plusSeconds(2), request.acknowledgedAt().orElseThrow());
        assertEquals(NOW.plusSeconds(3), request.completedAt().orElseThrow());
    }

    @Test
    void rejectsInvalidTransitionAndScopeMismatch() {
        DeviceCommandRequest request = request();

        assertThrows(IllegalStateException.class, () -> request.apply(event(request,
                DeviceCommandStatusChanged.Status.SUCCEEDED, NOW.plusSeconds(1), null)));
        assertThrows(IllegalArgumentException.class, () -> request.apply(new DeviceCommandStatusChanged(1,
                UUID.randomUUID(), request.commandId(), UUID.randomUUID(), request.deviceId(),
                DeviceCommandStatusChanged.Status.DISPATCHED, null, NOW.plusSeconds(1))));
    }

    @Test
    void failedProjectionCarriesFailureCode() {
        DeviceCommandRequest request = request();
        request.apply(event(request, DeviceCommandStatusChanged.Status.FAILED,
                NOW.plusSeconds(1), "PROVIDER_OFFLINE"));

        assertEquals(DeviceCommandStatusChanged.Status.FAILED, request.status());
        assertEquals("PROVIDER_OFFLINE", request.failureCode().orElseThrow());
    }

    private static DeviceCommandRequest request() {
        return DeviceCommandRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "request-1", "reboot", DIGEST,
                NOW, NOW.plusSeconds(30));
    }

    private static DeviceCommandStatusChanged event(DeviceCommandRequest request,
            DeviceCommandStatusChanged.Status status, Instant occurredAt, String failureCode) {
        return new DeviceCommandStatusChanged(1, UUID.randomUUID(), request.commandId(), request.tenantId(),
                request.deviceId(), status, failureCode, occurredAt);
    }
}

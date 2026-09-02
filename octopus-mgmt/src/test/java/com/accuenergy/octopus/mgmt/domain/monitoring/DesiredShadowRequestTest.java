package com.accuenergy.octopus.mgmt.domain.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DesiredShadowRequestTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void followsCommandDeliveryThenReportedApplication() {
        DesiredShadowRequest request = request();

        assertTrue(request.applyCommandStatus(event(request, DeviceCommandStatusChanged.Status.DISPATCHED,
                NOW.plusSeconds(1), null)));
        assertTrue(request.applyCommandStatus(event(request, DeviceCommandStatusChanged.Status.ACKNOWLEDGED,
                NOW.plusSeconds(2), null)));
        assertTrue(request.applyCommandStatus(event(request, DeviceCommandStatusChanged.Status.SUCCEEDED,
                NOW.plusSeconds(3), null)));
        assertTrue(request.markApplied(1, NOW.plusSeconds(4)));

        assertEquals(DesiredShadowRequest.Status.APPLIED, request.status());
        assertEquals(4, request.projectionVersion());
    }

    @Test
    void ignoresWrongAppliedVersionAndLateCommandAfterApplied() {
        DesiredShadowRequest request = request();
        assertFalse(request.markApplied(2, NOW.plusSeconds(1)));
        assertTrue(request.markApplied(1, NOW.plusSeconds(2)));
        assertFalse(request.applyCommandStatus(event(request, DeviceCommandStatusChanged.Status.SUCCEEDED,
                NOW.plusSeconds(3), null)));
    }

    private static DesiredShadowRequest request() {
        return DesiredShadowRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "desired-1", 0, "{\"relay\":true}",
                "0".repeat(64), UUID.randomUUID(), NOW, NOW.plusSeconds(30));
    }

    private static DeviceCommandStatusChanged event(DesiredShadowRequest request,
            DeviceCommandStatusChanged.Status status, Instant at, String failureCode) {
        return new DeviceCommandStatusChanged(1, UUID.randomUUID(), request.commandId(), request.tenantId(),
                request.deviceId(), status, failureCode, at);
    }
}

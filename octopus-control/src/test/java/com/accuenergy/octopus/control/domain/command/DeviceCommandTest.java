package com.accuenergy.octopus.control.domain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void followsDispatchAcknowledgeSuccessLifecycleAndVersionsEachMutationOnce() {
        DeviceCommand command = command();

        command.markDispatched(NOW.plusSeconds(1));
        command.acknowledge(NOW.plusSeconds(2));
        command.complete(NOW.plusSeconds(3));

        assertEquals(DeviceCommand.Status.SUCCEEDED, command.status());
        assertEquals(3, command.version());
        assertEquals(NOW.plusSeconds(3), command.completedAt().orElseThrow());
    }

    @Test
    void acceptsDirectSuccessAndSynthesizesAcknowledgement() {
        DeviceCommand command = command();
        command.markDispatched(NOW.plusSeconds(1));

        assertTrue(command.apply(result(command, DeviceCommandResult.Status.SUCCEEDED, NOW.plusSeconds(2))));

        assertEquals(DeviceCommand.Status.SUCCEEDED, command.status());
        assertEquals(NOW.plusSeconds(2), command.acknowledgedAt().orElseThrow());
        assertEquals(2, command.version());
    }

    @Test
    void lateAcknowledgementBecomesExpiredInsteadOfPoisoningRedelivery() {
        DeviceCommand command = command();
        command.markDispatched(NOW.plusSeconds(1));

        assertTrue(command.apply(result(command, DeviceCommandResult.Status.ACKNOWLEDGED, NOW.plusSeconds(30))));

        assertEquals(DeviceCommand.Status.EXPIRED, command.status());
        assertEquals(NOW.plusSeconds(30), command.completedAt().orElseThrow());
        assertEquals(2, command.version());
    }

    @Test
    void duplicateAcknowledgementIsIdempotent() {
        DeviceCommand command = command();
        command.markDispatched(NOW.plusSeconds(1));
        assertTrue(command.apply(result(command, DeviceCommandResult.Status.ACKNOWLEDGED, NOW.plusSeconds(2))));

        assertFalse(command.apply(result(command, DeviceCommandResult.Status.ACKNOWLEDGED, NOW.plusSeconds(3))));
        assertEquals(2, command.version());
    }

    @Test
    void rejectsResultThatPredatesDispatch() {
        DeviceCommand command = command();
        command.markDispatched(NOW.plusSeconds(2));

        assertThrows(IllegalArgumentException.class,
                () -> command.apply(result(command, DeviceCommandResult.Status.SUCCEEDED, NOW.plusSeconds(1))));
        assertEquals(DeviceCommand.Status.DISPATCHED, command.status());
        assertEquals(1, command.version());
    }

    private static DeviceCommand command() {
        return DeviceCommand.accept(request());
    }

    static DeviceCommandRequested request() {
        return new DeviceCommandRequested(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "request-1", "reboot", new byte[]{1},
                NOW, NOW.plusSeconds(30));
    }

    private static DeviceCommandResult result(DeviceCommand command, DeviceCommandResult.Status status, Instant at) {
        return new DeviceCommandResult(1, command.commandId(), command.tenantId(), command.deviceId(),
                status, status == DeviceCommandResult.Status.FAILED ? "DEVICE_ERROR" : null, at);
    }
}

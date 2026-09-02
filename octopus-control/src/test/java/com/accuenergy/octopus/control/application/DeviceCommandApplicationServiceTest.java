package com.accuenergy.octopus.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandResult;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.control.application.port.DeviceCommandRepository;
import com.accuenergy.octopus.control.application.port.DeviceMessagingPort;
import com.accuenergy.octopus.control.domain.command.DeviceCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void dispatchPersistsAcceptedAndDispatchedEventsAroundProviderSend() {
        MemoryRepository repository = new MemoryRepository();
        CapturingMessaging messaging = new CapturingMessaging(NOW.plusSeconds(1));
        DeviceCommandRequested request = request();

        DeviceCommand command = new DispatchDeviceCommandService(repository, messaging).dispatch(request);

        assertEquals(DeviceCommand.Status.DISPATCHED, command.status());
        assertEquals(List.of(DeviceCommandStatusChanged.Status.ACCEPTED,
                DeviceCommandStatusChanged.Status.DISPATCHED), repository.statuses());
        assertEquals(request.commandId(), messaging.command.envelope().commandId());
        assertEquals(request.operation(), messaging.command.envelope().operation());
    }

    @Test
    void duplicateRequestReturnsPersistedCommandWithoutRepublishing() {
        MemoryRepository repository = new MemoryRepository();
        DeviceCommandRequested request = request();
        DeviceCommand existing = DeviceCommand.accept(request);
        repository.command = existing;
        CapturingMessaging messaging = new CapturingMessaging(NOW.plusSeconds(1));

        assertSame(existing, new DispatchDeviceCommandService(repository, messaging).dispatch(request));
        assertEquals(0, messaging.sends);
    }

    @Test
    void providerFailurePropagatesSoKafkaTransactionCanRollback() {
        MemoryRepository repository = new MemoryRepository();
        DeviceMessagingPort unavailable = ignored -> {
            throw new DeviceMessagingUnavailableException("offline");
        };

        assertThrows(DeviceMessagingUnavailableException.class,
                () -> new DispatchDeviceCommandService(repository, unavailable).dispatch(request()));
        assertEquals(List.of(DeviceCommandStatusChanged.Status.ACCEPTED), repository.statuses());
        assertEquals(0, repository.updates);
    }

    @Test
    void resultServicePublishesAggregateOutcomeAndIgnoresDuplicateAck() {
        MemoryRepository repository = new MemoryRepository();
        DeviceCommand command = DeviceCommand.accept(request());
        command.markDispatched(NOW.plusSeconds(1));
        repository.command = command;
        ApplyDeviceCommandResultService service = new ApplyDeviceCommandResultService(repository);
        DeviceCommandResult ack = new DeviceCommandResult(1, command.commandId(), command.tenantId(),
                command.deviceId(), DeviceCommandResult.Status.ACKNOWLEDGED, null, NOW.plusSeconds(2));

        service.apply(ack);
        service.apply(new DeviceCommandResult(1, command.commandId(), command.tenantId(), command.deviceId(),
                DeviceCommandResult.Status.ACKNOWLEDGED, null, NOW.plusSeconds(3)));

        assertEquals(1, repository.updates);
        assertEquals(DeviceCommandStatusChanged.Status.ACKNOWLEDGED,
                repository.events.getFirst().status());
    }

    @Test
    void lateSuccessIsProjectedAsExpired() {
        MemoryRepository repository = new MemoryRepository();
        DeviceCommand command = DeviceCommand.accept(request());
        command.markDispatched(NOW.plusSeconds(1));
        repository.command = command;

        new ApplyDeviceCommandResultService(repository).apply(new DeviceCommandResult(1, command.commandId(),
                command.tenantId(), command.deviceId(), DeviceCommandResult.Status.SUCCEEDED, null,
                NOW.plusSeconds(30)));

        assertEquals(DeviceCommandStatusChanged.Status.EXPIRED, repository.events.getFirst().status());
    }

    private static DeviceCommandRequested request() {
        return new DeviceCommandRequested(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "request-1", "reboot", new byte[]{1},
                NOW, NOW.plusSeconds(30));
    }

    private static final class CapturingMessaging implements DeviceMessagingPort {
        private final Instant acceptedAt;
        private DeviceCommand command;
        private int sends;
        private CapturingMessaging(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
        @Override public CommandReceipt send(DeviceCommand command) {
            this.command = command;
            sends++;
            return new CommandReceipt(command.envelope().commandId(), CommandReceipt.Status.ACCEPTED, acceptedAt);
        }
    }

    private static final class MemoryRepository implements DeviceCommandRepository {
        private DeviceCommand command;
        private final List<DeviceCommandStatusChanged> events = new ArrayList<>();
        private int updates;
        @Override public Optional<DeviceCommand> find(UUID tenantId, UUID commandId) {
            return command != null && command.tenantId().equals(tenantId) && command.commandId().equals(commandId)
                    ? Optional.of(command) : Optional.empty();
        }
        @Override public void insert(DeviceCommand command, DeviceCommandStatusChanged statusEvent) {
            this.command = command;
            events.add(statusEvent);
        }
        @Override public void update(DeviceCommand command, DeviceCommandStatusChanged statusEvent) {
            this.command = command;
            updates++;
            events.add(statusEvent);
        }
        private List<DeviceCommandStatusChanged.Status> statuses() {
            return events.stream().map(DeviceCommandStatusChanged::status).toList();
        }
    }
}

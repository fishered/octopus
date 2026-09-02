package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.api.control.DeviceCommandEnvelope;
import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.control.application.port.DeviceCommandRepository;
import com.accuenergy.octopus.control.application.port.DeviceMessagingPort;
import com.accuenergy.octopus.control.domain.command.DeviceCommand;

public final class DispatchDeviceCommandService {
    private final DeviceCommandRepository repository;
    private final DeviceMessagingPort messaging;

    public DispatchDeviceCommandService(DeviceCommandRepository repository, DeviceMessagingPort messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    public DeviceCommand dispatch(DeviceCommandRequested request) {
        var existing = repository.find(request.tenantId(), request.commandId());
        if (existing.isPresent()) {
            DeviceCommand command = existing.orElseThrow();
            if (!command.matches(request)) throw new IllegalArgumentException("Duplicate command request conflicts with persisted command");
            return command;
        }
        DeviceCommand command = DeviceCommand.accept(request);
        repository.insert(command, status(command, DeviceCommandStatusChanged.Status.ACCEPTED,
                null, request.requestedAt()));
        var receipt = messaging.send(new DeviceMessagingPort.DeviceCommand(new DeviceCommandEnvelope(1,
                command.commandId(), command.tenantId(), command.deviceId(), command.operation(), command.payload(),
                command.createdAt(), command.expiresAt())));
        if (!command.commandId().equals(receipt.commandId())) {
            throw new DeviceMessagingUnavailableException("Device messaging receipt does not match command");
        }
        if (receipt.status() == DeviceMessagingPort.CommandReceipt.Status.ACCEPTED) {
            command.markDispatched(receipt.acceptedAt());
            repository.update(command, status(command, apiStatus(command.status()), null, receipt.acceptedAt()));
        } else if (receipt.status() == DeviceMessagingPort.CommandReceipt.Status.EXPIRED) {
            command.expire(receipt.acceptedAt());
            if (command.status() != DeviceCommand.Status.EXPIRED) {
                throw new DeviceMessagingUnavailableException("Device messaging provider reported premature expiry");
            }
            repository.update(command, status(command, DeviceCommandStatusChanged.Status.EXPIRED,
                    null, receipt.acceptedAt()));
        } else {
            throw new DeviceMessagingUnavailableException("Device messaging provider rejected command");
        }
        return command;
    }

    private static DeviceCommandStatusChanged.Status apiStatus(DeviceCommand.Status status) {
        return DeviceCommandStatusChanged.Status.valueOf(status.name());
    }

    private static DeviceCommandStatusChanged status(DeviceCommand command,
            DeviceCommandStatusChanged.Status status, String failureCode, java.time.Instant occurredAt) {
        return new DeviceCommandStatusChanged(1, java.util.UUID.randomUUID(), command.commandId(),
                command.tenantId(), command.deviceId(), status, failureCode, occurredAt);
    }
}

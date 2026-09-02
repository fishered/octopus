package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.api.control.DeviceCommandResult;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.control.application.port.DeviceCommandRepository;
import com.accuenergy.octopus.control.domain.command.DeviceCommand;
import java.util.UUID;

public final class ApplyDeviceCommandResultService {
    private final DeviceCommandRepository commands;

    public ApplyDeviceCommandResultService(DeviceCommandRepository commands) {
        this.commands = commands;
    }

    public void apply(DeviceCommandResult result) {
        DeviceCommand command = commands.find(result.tenantId(), result.commandId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown device command"));
        if (!command.apply(result)) return;
        DeviceCommandStatusChanged.Status status = DeviceCommandStatusChanged.Status.valueOf(command.status().name());
        commands.update(command, new DeviceCommandStatusChanged(1, UUID.randomUUID(), command.commandId(),
                command.tenantId(), command.deviceId(), status, command.failureCode().orElse(null),
                result.occurredAt()));
    }
}

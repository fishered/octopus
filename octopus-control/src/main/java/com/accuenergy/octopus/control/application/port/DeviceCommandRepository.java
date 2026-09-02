package com.accuenergy.octopus.control.application.port;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.control.domain.command.DeviceCommand;
import java.util.Optional;
import java.util.UUID;

public interface DeviceCommandRepository {
    Optional<DeviceCommand> find(UUID tenantId, UUID commandId);
    void insert(DeviceCommand command, DeviceCommandStatusChanged statusEvent);
    void update(DeviceCommand command, DeviceCommandStatusChanged statusEvent);
}

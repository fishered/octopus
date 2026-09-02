package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import java.util.UUID;

public interface DevicePresenceRepository {
    DevicePresenceSnapshot find(UUID tenantId, UUID deviceId);
}

package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.mgmt.domain.monitoring.DeviceShadow;
import java.util.Optional;
import java.util.UUID;

public interface DeviceShadowRepository {
    void apply(DeviceShadowReported event);
    Optional<DeviceShadow> find(UUID deviceId);
}

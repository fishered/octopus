package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.mgmt.domain.control.DeviceCommandRequest;
import com.accuenergy.octopus.mgmt.domain.monitoring.DesiredShadowRequest;
import java.util.Optional;
import java.util.UUID;

public interface DeviceDesiredShadowRepository {
    Optional<DesiredShadowRequest> findByIdempotency(UUID requestedBy, String idempotencyKey);
    Optional<DesiredShadowRequest> findCurrent(UUID deviceId);
    DesiredShadowRequest submit(DesiredShadowRequest desired, DeviceCommandRequest command,
            DeviceCommandRequested event);
    void applyCommandStatus(DeviceCommandStatusChanged event);
    void reconcile(DeviceShadowReported event);
}

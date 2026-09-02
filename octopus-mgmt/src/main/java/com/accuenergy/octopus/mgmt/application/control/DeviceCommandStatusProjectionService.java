package com.accuenergy.octopus.mgmt.application.control;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceDesiredShadowRepository;

public final class DeviceCommandStatusProjectionService {
    private final DeviceCommandRequestRepository commands;
    private final DeviceDesiredShadowRepository desired;

    public DeviceCommandStatusProjectionService(DeviceCommandRequestRepository commands,
            DeviceDesiredShadowRepository desired) {
        this.commands = commands;
        this.desired = desired;
    }

    public void apply(DeviceCommandStatusChanged event) {
        var tenant = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Command status projection requires tenant scope"));
        if (!tenant.value().equals(event.tenantId())) throw new IllegalArgumentException("Status tenant mismatch");
        commands.applyStatus(event);
        desired.applyCommandStatus(event);
    }
}

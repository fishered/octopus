package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.common.tenant.TenantContext;

public final class DeviceShadowProjectionService {
    private final DeviceShadowRepository shadows;
    private final DeviceDesiredShadowRepository desired;

    public DeviceShadowProjectionService(DeviceShadowRepository shadows, DeviceDesiredShadowRepository desired) {
        this.shadows = shadows;
        this.desired = desired;
    }

    public void apply(DeviceShadowReported event) {
        var tenant = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Shadow projection requires tenant scope"));
        if (!tenant.value().equals(event.tenantId())) throw new IllegalArgumentException("Shadow tenant mismatch");
        shadows.apply(event);
        desired.reconcile(event);
    }
}

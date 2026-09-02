package com.accuenergy.octopus.mgmt.application.monitoring;

import com.accuenergy.octopus.api.control.DevicePresenceSnapshot;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.monitoring.DeviceShadow;
import java.util.Optional;
import java.util.UUID;

public final class DeviceMonitoringService {
    private final DeviceRepository devices;
    private final DevicePresenceRepository presence;
    private final DeviceShadowRepository shadows;
    private final AuthorizationPolicy authorization;

    public DeviceMonitoringService(DeviceRepository devices, DevicePresenceRepository presence,
            DeviceShadowRepository shadows, AuthorizationPolicy authorization) {
        this.devices = devices;
        this.presence = presence;
        this.shadows = shadows;
        this.authorization = authorization;
    }

    public DevicePresenceSnapshot presence(AuthenticatedPrincipal principal, UUID deviceId) {
        TenantId tenant = requireAccess(principal, deviceId);
        return presence.find(tenant.value(), deviceId);
    }

    public DeviceShadow shadow(AuthenticatedPrincipal principal, UUID deviceId) {
        requireAccess(principal, deviceId);
        return shadows.find(deviceId).orElseThrow(() ->
                new DeviceMonitoringNotFoundException("Device has not reported a shadow"));
    }

    private TenantId requireAccess(AuthenticatedPrincipal principal, UUID deviceId) {
        TenantId tenant = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("Device monitoring requires tenant scope"));
        DeviceRepository.DeviceDetails details = devices.findDetails(deviceId)
                .orElseThrow(() -> new DeviceMonitoringNotFoundException("Unknown device"));
        if (!details.device().tenantId().equals(tenant.value())) throw new DeviceMonitoringAccessDeniedException();
        if (!authorization.isAllowed(principal, "device:view", ResourceAction.VIEW,
                new ProtectedResource(tenant, "device", deviceId, Optional.of(details.organizationPath())))) {
            throw new DeviceMonitoringAccessDeniedException();
        }
        return tenant;
    }
}

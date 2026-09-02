package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class DeviceManagementService {
    private final DeviceRepository devices;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public DeviceManagementService(DeviceRepository devices, AuthorizationPolicy authorization, Clock clock) {
        this.devices = devices; this.authorization = authorization; this.clock = clock;
    }

    public Device register(AuthenticatedPrincipal principal, RegisterDevice command) {
        TenantId tenantId = currentTenant();
        String path = devices.findOrganizationPath(command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        if (!authorization.isAllowed(principal, "device:create", ResourceAction.CONFIGURE,
                new ProtectedResource(tenantId, "organization", command.organizationId(), Optional.of(path)))) {
            throw new DeviceAccessDeniedException();
        }
        if (devices.existsByCode(command.code())) throw new IllegalArgumentException("Device code already exists");
        Device device = Device.register(UUID.randomUUID(), tenantId.value(), command.organizationId(),
                command.facilityId(), command.deviceTypeId(), command.thingModelId(), command.modelVersion(),
                command.code(), command.displayName(), clock.instant());
        devices.insert(device);
        return device;
    }

    public Device get(AuthenticatedPrincipal principal, UUID deviceId) {
        DeviceRepository.DeviceDetails details = devices.findDetails(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        if (!authorization.isAllowed(principal, "device:view", ResourceAction.VIEW,
                new ProtectedResource(new TenantId(details.device().tenantId()), "device", deviceId,
                        Optional.of(details.organizationPath())))) throw new DeviceAccessDeniedException();
        return details.device();
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record RegisterDevice(UUID organizationId, UUID facilityId, UUID deviceTypeId,
                                 UUID thingModelId, long modelVersion, String code, String displayName) { }
}

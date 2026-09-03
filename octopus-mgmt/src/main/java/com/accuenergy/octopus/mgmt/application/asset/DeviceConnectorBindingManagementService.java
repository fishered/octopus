package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class DeviceConnectorBindingManagementService {
    private final DeviceConnectorBindingRepository bindings; private final DeviceActualRepository devices;
    private final AuthorizationPolicy authorization; private final Clock clock;
    public DeviceConnectorBindingManagementService(DeviceConnectorBindingRepository bindings, DeviceActualRepository devices,
            AuthorizationPolicy authorization, Clock clock) { this.bindings=bindings; this.devices=devices; this.authorization=authorization; this.clock=clock; }
    public Optional<DeviceConnectorBinding> get(AuthenticatedPrincipal principal, UUID deviceId) {
        requireAccess(principal, deviceId, "device:view", ResourceAction.VIEW);
        return bindings.find(deviceId);
    }
    public DeviceConnectorBinding upsert(AuthenticatedPrincipal principal, UUID deviceId, String pluginId, String codecId,
            String configRef, DeviceConnectorBinding.Status status, Long expectedVersion) {
        TenantId tenant=currentTenant(); var context=requireAccess(principal, deviceId, "device:configure", ResourceAction.CONFIGURE);
        if (!context.tenantId().equals(tenant.value())) throw new DeviceConnectorBindingAccessDeniedException();
        Optional<DeviceConnectorBinding> current=bindings.find(deviceId);
        if (current.isEmpty()) {
            if (expectedVersion != null && expectedVersion != 0) throw new IllegalArgumentException("If-Match does not match binding");
            DeviceConnectorBinding created=DeviceConnectorBinding.create(tenant.value(),deviceId,pluginId,codecId,configRef,status,clock.instant());
            bindings.insert(created); return created;
        }
        DeviceConnectorBinding existing=current.get();
        if (expectedVersion == null || expectedVersion != existing.version()) throw new IllegalArgumentException("If-Match does not match binding");
        DeviceConnectorBinding updated=existing.next(pluginId,codecId,configRef,status,clock.instant());
        bindings.update(updated, existing.version()); return updated;
    }
    private DeviceActualRepository.DeviceContext requireAccess(AuthenticatedPrincipal principal, UUID deviceId, String permission, ResourceAction action) {
        TenantId tenant=currentTenant(); var device=devices.findDeviceContext(deviceId).orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        if (!device.tenantId().equals(tenant.value())) throw new DeviceConnectorBindingAccessDeniedException();
        if (!authorization.isAllowed(principal, permission, action, new ProtectedResource(tenant,"device",deviceId,Optional.of(device.organizationPath()))))
            throw new DeviceConnectorBindingAccessDeniedException();
        return device;
    }
    private static TenantId currentTenant() { return TenantContext.requireCurrent().tenantId().orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required")); }
}

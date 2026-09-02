package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class DeviceActualManagementService {
    private final DeviceActualRepository actuals;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public DeviceActualManagementService(DeviceActualRepository actuals,
                                         AuthorizationPolicy authorization, Clock clock) {
        this.actuals = actuals;
        this.authorization = authorization;
        this.clock = clock;
    }

    public DeviceActual commission(AuthenticatedPrincipal principal, CommissionDeviceActual command) {
        TenantId tenantId = currentTenant();
        DeviceActualRepository.DeviceContext device = requireDeviceAccess(principal, tenantId, command.deviceId(),
                "device:configure", ResourceAction.CONFIGURE);
        if (!device.tenantId().equals(tenantId.value())) throw new DeviceActualAccessDeniedException();
        if (actuals.deviceAlreadyCommissioned(command.deviceId())) {
            throw new IllegalArgumentException("Device already has commissioned hardware");
        }
        if (actuals.hardwareSerialExists(command.hardwareSerial())) {
            throw new IllegalArgumentException("Hardware serial already exists");
        }
        if (command.certificateId() != null && actuals.certificateInUse(command.certificateId(), null)) {
            throw new IllegalArgumentException("Certificate is already bound to hardware");
        }
        DeviceActual actual = DeviceActual.commission(UUID.randomUUID(), tenantId.value(), command.deviceId(),
                command.hardwareSerial(), command.manufacturer(), command.firmwareVersion(),
                command.certificateId(), clock.instant());
        actuals.insert(actual);
        return actual;
    }

    public DeviceActual get(AuthenticatedPrincipal principal, UUID deviceId) {
        DeviceActualRepository.DeviceActualDetails details = actuals.findDetailsByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device hardware is not commissioned"));
        if (!authorization.isAllowed(principal, "device:view", ResourceAction.VIEW,
                new ProtectedResource(new TenantId(details.actual().tenantId()), "device", deviceId,
                        Optional.of(details.organizationPath())))) {
            throw new DeviceActualAccessDeniedException();
        }
        return details.actual();
    }

    public DeviceActual bindCertificate(AuthenticatedPrincipal principal, UUID deviceId, UUID certificateId) {
        TenantId tenantId = currentTenant();
        DeviceActualRepository.DeviceContext device = requireDeviceAccess(principal, tenantId, deviceId,
                "device:configure", ResourceAction.CONFIGURE);
        if (!device.tenantId().equals(tenantId.value())) throw new DeviceActualAccessDeniedException();
        DeviceActual actual = actuals.findDetailsByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device hardware is not commissioned"))
                .actual();
        if (actuals.certificateInUse(certificateId, actual.id())) {
            throw new IllegalArgumentException("Certificate is already bound to other hardware");
        }
        actual.bindCertificate(certificateId, clock.instant());
        actuals.update(actual);
        return actuals.findDetailsByDeviceId(deviceId).orElseThrow().actual();
    }

    private DeviceActualRepository.DeviceContext requireDeviceAccess(AuthenticatedPrincipal principal,
            TenantId tenantId, UUID deviceId, String permission, ResourceAction action) {
        DeviceActualRepository.DeviceContext device = actuals.findDeviceContext(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        if (!authorization.isAllowed(principal, permission, action,
                new ProtectedResource(tenantId, "device", deviceId, Optional.of(device.organizationPath())))) {
            throw new DeviceActualAccessDeniedException();
        }
        return device;
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record CommissionDeviceActual(UUID deviceId, String hardwareSerial, String manufacturer,
                                         String firmwareVersion, UUID certificateId) { }
}

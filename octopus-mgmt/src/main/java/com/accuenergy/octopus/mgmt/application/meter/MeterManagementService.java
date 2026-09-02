package com.accuenergy.octopus.mgmt.application.meter;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class MeterManagementService {
    private final MeterRepository meters;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public MeterManagementService(MeterRepository meters, AuthorizationPolicy authorization, Clock clock) {
        this.meters = meters;
        this.authorization = authorization;
        this.clock = clock;
    }

    public Meter create(AuthenticatedPrincipal principal, CreateMeter command) {
        TenantId tenantId = currentTenant();
        MeterRepository.DeviceContext device = meters.findDeviceContext(command.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown device"));
        if (!authorization.isAllowed(principal, "meter:create", ResourceAction.CONFIGURE,
                new ProtectedResource(tenantId, "device", command.deviceId(), Optional.of(device.organizationPath())))) {
            throw new MeterAccessDeniedException();
        }
        if (!device.tenantId().equals(tenantId.value())) throw new MeterAccessDeniedException();
        if (meters.existsByCode(command.code())) throw new IllegalArgumentException("Meter code already exists");
        if (command.facilityId() != null && !meters.facilityExists(command.facilityId())) {
            throw new IllegalArgumentException("Unknown facility");
        }
        if (!meters.isBoundToDeviceModel(command.deviceId(), command.parameterId(), command.unitId())) {
            throw new IllegalArgumentException("Parameter and unit are not part of the device model version");
        }
        MeterRepository.ParameterConfiguration parameter = meters
                .findParameterConfiguration(command.parameterId(), command.unitId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown parameter or incompatible unit"));
        int decimalScale = command.decimalScale() == null
                ? parameter.defaultDecimalScale() : command.decimalScale();
        var now = clock.instant();
        Meter meter = Meter.create(UUID.randomUUID(), tenantId.value(), command.deviceId(), command.facilityId(),
                command.parameterId(), command.unitId(), command.code(), command.displayName(), command.kind(),
                command.calculationExpression(), command.rolloverModulus(), decimalScale,
                parameter.semantics(), now);
        MeterConfigurationChanged event = new MeterConfigurationChanged(1, UUID.randomUUID(), tenantId.value(),
                meter.id(), device.modelVersion(), meter.parameterId(),
                MeterConfigurationChanged.ValueSemantics.valueOf(parameter.semantics().name()),
                parameter.sourceUnitCode(), parameter.canonicalUnitCode(), parameter.scale(), parameter.offset(),
                meter.rolloverModulus().orElse(null), meter.version(), 1, now);
        meters.insert(meter, event);
        return meter;
    }

    public Meter get(AuthenticatedPrincipal principal, UUID meterId) {
        MeterRepository.MeterDetails details = meters.findDetails(meterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown meter"));
        if (!authorization.isAllowed(principal, "meter:view", ResourceAction.VIEW,
                new ProtectedResource(new TenantId(details.meter().tenantId()), "meter", meterId,
                        Optional.of(details.organizationPath())))) {
            throw new MeterAccessDeniedException();
        }
        return details.meter();
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record CreateMeter(UUID deviceId, UUID facilityId, UUID parameterId, UUID unitId,
                              String code, String displayName, Meter.Kind kind,
                              String calculationExpression, BigDecimal rolloverModulus,
                              Integer decimalScale) { }
}

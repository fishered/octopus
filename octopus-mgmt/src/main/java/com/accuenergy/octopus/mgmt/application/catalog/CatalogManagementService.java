package com.accuenergy.octopus.mgmt.application.catalog;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.catalog.DeviceType;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.domain.catalog.ThingModel;
import com.accuenergy.octopus.mgmt.domain.catalog.UnitDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CatalogManagementService {
    private final CatalogRepository catalog;
    private final Clock clock;

    public CatalogManagementService(CatalogRepository catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public DeviceType createDeviceType(AuthenticatedPrincipal principal, CreateDeviceType command) {
        TenantId tenantId = requireTenantPermission(principal, "catalog:configure");
        if (catalog.deviceTypeCodeExists(command.code())) {
            throw new IllegalArgumentException("Device type code already exists");
        }
        DeviceType deviceType = DeviceType.createTenant(UUID.randomUUID(), tenantId.value(), command.code(),
                command.displayName(), command.capabilities().toString(), clock.instant());
        catalog.insertDeviceType(deviceType);
        return deviceType;
    }

    public DeviceType getDeviceType(AuthenticatedPrincipal principal, UUID deviceTypeId) {
        requireTenantPermission(principal, "catalog:view");
        return catalog.findDeviceType(deviceTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device type"));
    }

    public UnitDefinition createGlobalUnit(AuthenticatedPrincipal principal, CreateUnit command) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new CatalogAccessDeniedException();
        }
        if (catalog.unitCodeExists(command.code())) throw new IllegalArgumentException("Unit code already exists");
        UnitDefinition unit = new UnitDefinition(UUID.randomUUID(), command.code(), command.symbol(),
                command.dimension(), command.scale(), command.offset(), Optional.ofNullable(command.description()));
        catalog.insertUnit(unit);
        return unit;
    }

    public UnitDefinition getUnit(AuthenticatedPrincipal principal, UUID unitId) {
        requireTenantPermission(principal, "catalog:view");
        return catalog.findUnit(unitId).orElseThrow(() -> new IllegalArgumentException("Unknown unit"));
    }

    public ParameterDefinition createParameter(AuthenticatedPrincipal principal, CreateParameter command) {
        TenantId tenantId = requireTenantPermission(principal, "catalog:configure");
        if (catalog.parameterCodeExists(command.code())) {
            throw new IllegalArgumentException("Parameter code already exists");
        }
        command.canonicalUnitId().ifPresent(unitId -> catalog.findUnit(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown canonical unit")));
        ParameterDefinition parameter = new ParameterDefinition(UUID.randomUUID(), Optional.of(tenantId.value()),
                command.code(), command.displayName(), command.quantityKind(), command.valueSemantics(),
                command.dataType(), command.canonicalUnitId(), command.decimalScale(),
                ParameterDefinition.Status.ACTIVE);
        catalog.insertParameter(parameter);
        return parameter;
    }

    public ParameterDefinition getParameter(AuthenticatedPrincipal principal, UUID parameterId) {
        requireTenantPermission(principal, "catalog:view");
        return catalog.findParameter(parameterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown parameter"));
    }

    public ThingModel createThingModel(AuthenticatedPrincipal principal, CreateThingModel command) {
        TenantId tenantId = requireTenantPermission(principal, "catalog:configure");
        DeviceType deviceType = catalog.findDeviceType(command.deviceTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown device type"));
        if (deviceType.status() != DeviceType.Status.ACTIVE) {
            throw new IllegalArgumentException("Retired device type cannot be used by a thing model");
        }
        if (catalog.thingModelVersionExists(command.code(), command.modelVersion())) {
            throw new IllegalArgumentException("Thing model version already exists");
        }
        for (ThingModel.ParameterBinding binding : command.parameters()) validateBinding(binding);
        ThingModel model = ThingModel.draft(UUID.randomUUID(), tenantId.value(), command.deviceTypeId(),
                command.code(), command.displayName(), command.modelVersion(), command.schemaDocument(),
                command.parameters(), clock.instant());
        catalog.insertThingModel(model);
        return model;
    }

    public ThingModel publishThingModel(AuthenticatedPrincipal principal, UUID thingModelId) {
        requireTenantPermission(principal, "catalog:configure");
        ThingModel model = catalog.findThingModel(thingModelId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown thing model"));
        model.publish(clock.instant());
        catalog.updateThingModelLifecycle(model);
        return model;
    }

    public ThingModel getThingModel(AuthenticatedPrincipal principal, UUID thingModelId) {
        requireTenantPermission(principal, "catalog:view");
        return catalog.findThingModel(thingModelId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown thing model"));
    }

    private void validateBinding(ThingModel.ParameterBinding binding) {
        ParameterDefinition parameter = catalog.findParameter(binding.parameterId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown parameter: " + binding.parameterId()));
        if (parameter.status() != ParameterDefinition.Status.ACTIVE) {
            throw new IllegalArgumentException("Retired parameter cannot be added to a model");
        }
        UUID canonicalUnit = parameter.canonicalUnitId()
                .orElseThrow(() -> new IllegalArgumentException("Only numeric telemetry parameters can be bound"));
        if (!catalog.unitsHaveSameDimension(canonicalUnit, binding.unitId())) {
            throw new IllegalArgumentException("Parameter and source unit dimensions differ");
        }
    }

    private static TenantId requireTenantPermission(AuthenticatedPrincipal principal, String permission) {
        TenantId tenantId = TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) return tenantId;
        if (!principal.tenantId().orElseThrow().equals(tenantId) || !principal.permissions().contains(permission)) {
            throw new CatalogAccessDeniedException();
        }
        return tenantId;
    }

    public record CreateUnit(String code, String symbol, String dimension, BigDecimal scale,
                             BigDecimal offset, String description) { }

    public record CreateDeviceType(String code, String displayName, JsonNode capabilities) {
        public CreateDeviceType {
            Objects.requireNonNull(capabilities, "capabilities");
            if (!capabilities.isObject()) {
                throw new IllegalArgumentException("Device type capabilities must be a JSON object");
            }
            capabilities = capabilities.deepCopy();
        }
    }

    public record CreateParameter(String code, String displayName, String quantityKind,
                                  ParameterDefinition.ValueSemantics valueSemantics,
                                  ParameterDefinition.DataType dataType, Optional<UUID> canonicalUnitId,
                                  Optional<Integer> decimalScale) { }

    public record CreateThingModel(UUID deviceTypeId, String code, String displayName, long modelVersion,
                                   String schemaDocument, List<ThingModel.ParameterBinding> parameters) {
        public CreateThingModel { parameters = List.copyOf(parameters); }
    }
}

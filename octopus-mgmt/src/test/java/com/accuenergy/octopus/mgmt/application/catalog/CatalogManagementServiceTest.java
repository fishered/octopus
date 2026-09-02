package com.accuenergy.octopus.mgmt.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.catalog.DeviceType;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.domain.catalog.ThingModel;
import com.accuenergy.octopus.mgmt.domain.catalog.UnitDefinition;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final MemoryCatalog repository = new MemoryCatalog();
    private final CatalogManagementService service = new CatalogManagementService(repository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsTenantDeviceTypeWithDeclarationOnlyCapabilities() throws Exception {
        var capabilities = JsonNodeFactory.instance.objectNode()
                .put("protocol", "mqtt")
                .put("supportsCommands", true);

        DeviceType created = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.createDeviceType(operator("catalog:configure"),
                        new CatalogManagementService.CreateDeviceType(
                                "energy-meter", "Energy meter", capabilities)));
        DeviceType loaded = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.getDeviceType(operator("catalog:view"), created.id()));

        assertEquals(tenant.value(), created.tenantId().orElseThrow());
        assertEquals(DeviceType.Status.ACTIVE, created.status());
        assertEquals("{\"protocol\":\"mqtt\",\"supportsCommands\":true}",
                created.capabilitiesDocument());
        assertEquals(created.id(), loaded.id());
    }

    @Test
    void rejectsDuplicateDeviceTypeCodeInVisibleCatalog() {
        repository.deviceTypes.put(UUID.randomUUID(), deviceType("energy-meter", DeviceType.Status.ACTIVE));

        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.createDeviceType(operator("catalog:configure"),
                        deviceTypeCommand("energy-meter"))));
    }

    @Test
    void rejectsDeviceTypeCreationWithoutCatalogConfigurePermission() {
        assertThrows(CatalogAccessDeniedException.class,
                () -> TenantContext.run(new TenantScope.Scoped(tenant),
                        () -> service.createDeviceType(operator("catalog:view"),
                                deviceTypeCommand("energy-meter"))));
    }

    @Test
    void rejectsNonObjectDeviceTypeCapabilities() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogManagementService.CreateDeviceType("gateway", "Gateway",
                        JsonNodeFactory.instance.arrayNode().add("mqtt")));
    }

    @Test
    void createsAndPublishesVersionedThingModel() throws Exception {
        UnitDefinition unit = unit("kWh", "energy");
        repository.units.put(unit.id(), unit);
        ParameterDefinition parameter = parameter(unit.id());
        repository.parameters.put(parameter.id(), parameter);
        DeviceType deviceType = deviceType("energy-meter", DeviceType.Status.ACTIVE);
        repository.deviceTypes.put(deviceType.id(), deviceType);
        UUID deviceTypeId = deviceType.id();
        var command = new CatalogManagementService.CreateThingModel(deviceTypeId, "energy-meter",
                "Energy meter", 1, "{}", List.of(
                new ThingModel.ParameterBinding(parameter.id(), unit.id(), true, 0)));

        ThingModel draft = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.createThingModel(operator("catalog:configure"), command));
        ThingModel published = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.publishThingModel(operator("catalog:configure"), draft.id()));

        assertEquals(ThingModel.Status.PUBLISHED, published.status());
        assertEquals(tenant.value(), published.tenantId().orElseThrow());
    }

    @Test
    void rejectsBindingUnitsFromAnotherDimension() {
        UnitDefinition canonical = unit("kWh", "energy");
        UnitDefinition volts = unit("V", "voltage");
        repository.units.put(canonical.id(), canonical);
        repository.units.put(volts.id(), volts);
        ParameterDefinition parameter = parameter(canonical.id());
        repository.parameters.put(parameter.id(), parameter);
        DeviceType deviceType = deviceType("meter", DeviceType.Status.ACTIVE);
        repository.deviceTypes.put(deviceType.id(), deviceType);
        var command = new CatalogManagementService.CreateThingModel(deviceType.id(), "bad", "Bad", 1,
                "{}", List.of(new ThingModel.ParameterBinding(parameter.id(), volts.id(), true, 0)));

        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.createThingModel(operator("catalog:configure"), command)));
    }

    @Test
    void rejectsThingModelForRetiredDeviceType() {
        DeviceType retired = deviceType("legacy-meter", DeviceType.Status.RETIRED);
        repository.deviceTypes.put(retired.id(), retired);
        var command = new CatalogManagementService.CreateThingModel(retired.id(), "legacy", "Legacy", 1,
                "{}", List.of());

        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.createThingModel(operator("catalog:configure"), command)));
    }

    @Test
    void rejectsThingModelForUnknownDeviceType() {
        var command = new CatalogManagementService.CreateThingModel(UUID.randomUUID(), "unknown", "Unknown", 1,
                "{}", List.of());

        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.createThingModel(operator("catalog:configure"), command)));
    }

    private UnitDefinition unit(String code, String dimension) {
        return new UnitDefinition(UUID.randomUUID(), code, code, dimension, BigDecimal.ONE,
                BigDecimal.ZERO, Optional.empty());
    }

    private ParameterDefinition parameter(UUID unitId) {
        return new ParameterDefinition(UUID.randomUUID(), Optional.of(tenant.value()), "energy", "Energy",
                "energy", ParameterDefinition.ValueSemantics.CUMULATIVE,
                ParameterDefinition.DataType.DECIMAL, Optional.of(unitId), Optional.of(3),
                ParameterDefinition.Status.ACTIVE);
    }

    private DeviceType deviceType(String code, DeviceType.Status status) {
        return DeviceType.restore(UUID.randomUUID(), tenant.value(), code, code, "{}", status, NOW);
    }

    private CatalogManagementService.CreateDeviceType deviceTypeCommand(String code) {
        return new CatalogManagementService.CreateDeviceType(code, code,
                JsonNodeFactory.instance.objectNode().put("protocol", "mqtt"));
    }

    private AuthenticatedPrincipal operator(String permission) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of("/root"), Set.of());
    }

    private static final class MemoryCatalog implements CatalogRepository {
        private final Map<UUID, DeviceType> deviceTypes = new HashMap<>();
        private final Map<UUID, UnitDefinition> units = new HashMap<>();
        private final Map<UUID, ParameterDefinition> parameters = new HashMap<>();
        private final Map<UUID, ThingModel> models = new HashMap<>();
        public boolean deviceTypeCodeExists(String code) {
            return deviceTypes.values().stream().anyMatch(v -> v.code().equals(code));
        }
        public void insertDeviceType(DeviceType deviceType) { deviceTypes.put(deviceType.id(), deviceType); }
        public Optional<DeviceType> findDeviceType(UUID id) { return Optional.ofNullable(deviceTypes.get(id)); }
        public boolean unitCodeExists(String code) { return units.values().stream().anyMatch(v -> v.code().equals(code)); }
        public void insertUnit(UnitDefinition unit) { units.put(unit.id(), unit); }
        public Optional<UnitDefinition> findUnit(UUID id) { return Optional.ofNullable(units.get(id)); }
        public boolean parameterCodeExists(String code) { return parameters.values().stream().anyMatch(v -> v.code().equals(code)); }
        public void insertParameter(ParameterDefinition parameter) { parameters.put(parameter.id(), parameter); }
        public Optional<ParameterDefinition> findParameter(UUID id) { return Optional.ofNullable(parameters.get(id)); }
        public boolean thingModelVersionExists(String code, long version) { return false; }
        public void insertThingModel(ThingModel model) { models.put(model.id(), model); }
        public Optional<ThingModel> findThingModel(UUID id) { return Optional.ofNullable(models.get(id)); }
        public void updateThingModelLifecycle(ThingModel model) { models.put(model.id(), model); }
        public boolean unitsHaveSameDimension(UUID first, UUID second) {
            return units.get(first).dimension().equals(units.get(second).dimension());
        }
    }
}

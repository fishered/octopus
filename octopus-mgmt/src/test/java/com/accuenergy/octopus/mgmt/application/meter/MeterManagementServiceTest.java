package com.accuenergy.octopus.mgmt.application.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeterManagementServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final MemoryMeterRepository repository = new MemoryMeterRepository();
    private final MeterManagementService service = new MeterManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsMeterAndAtomicallyRequestsConfigurationProjection() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID parameterId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        repository.device = new MeterRepository.DeviceContext(tenant.value(), "/root/site-a", 7);
        repository.parameter = new MeterRepository.ParameterConfiguration(ValueSemantics.CUMULATIVE,
                "Wh", "kWh", new BigDecimal("0.001"), BigDecimal.ZERO, 3);
        var command = new MeterManagementService.CreateMeter(deviceId, null, parameterId, unitId,
                "energy-total", "Energy total", Meter.Kind.STANDARD, null,
                new BigDecimal("1000000"), null);

        Meter meter = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.create(operator("meter:create"), command));

        assertEquals(meter.id(), repository.event.meterId());
        assertEquals(7, repository.event.modelVersion());
        assertEquals(MeterConfigurationChanged.ValueSemantics.CUMULATIVE, repository.event.semantics());
        assertEquals(new BigDecimal("0.001"), repository.event.scale());
    }

    @Test
    void rejectsMeterNotDeclaredByPublishedDeviceModel() {
        repository.bound = false;
        repository.device = new MeterRepository.DeviceContext(tenant.value(), "/root/site-a", 1);
        var command = new MeterManagementService.CreateMeter(UUID.randomUUID(), null, UUID.randomUUID(),
                UUID.randomUUID(), "rogue", "Rogue", Meter.Kind.STANDARD, null, null, 2);
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.create(operator("meter:create"), command)));
    }

    private AuthenticatedPrincipal operator(String permission) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of("/root"), Set.of());
    }

    private static final class MemoryMeterRepository implements MeterRepository {
        private DeviceContext device;
        private ParameterConfiguration parameter;
        private boolean bound = true;
        private Meter meter;
        private MeterConfigurationChanged event;
        public boolean existsByCode(String code) { return false; }
        public Optional<DeviceContext> findDeviceContext(UUID id) { return Optional.ofNullable(device); }
        public Optional<ParameterConfiguration> findParameterConfiguration(UUID parameterId, UUID unitId) {
            return Optional.ofNullable(parameter);
        }
        public boolean isBoundToDeviceModel(UUID deviceId, UUID parameterId, UUID unitId) { return bound; }
        public boolean facilityExists(UUID facilityId) { return true; }
        public void insert(Meter meter, MeterConfigurationChanged event) { this.meter = meter; this.event = event; }
        public Optional<MeterDetails> findDetails(UUID id) {
            return meter == null ? Optional.empty()
                    : Optional.of(new MeterDetails(meter, "/root/site-a", "kWh", "Asia/Shanghai"));
        }
    }
}

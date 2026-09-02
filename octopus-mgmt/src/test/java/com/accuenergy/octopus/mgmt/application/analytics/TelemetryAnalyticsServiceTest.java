package com.accuenergy.octopus.mgmt.application.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelemetryAnalyticsServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID meterId = UUID.randomUUID();
    private final Meter meter = Meter.create(meterId, tenant.value(), UUID.randomUUID(), null,
            UUID.randomUUID(), UUID.randomUUID(), "energy", "Energy", Meter.Kind.STANDARD,
            null, null, 3, ValueSemantics.CUMULATIVE, Instant.parse("2026-01-01T00:00:00Z"));
    private final MeterRepository meters = new StubMeterRepository(meter);
    private final TelemetryQueryPort telemetry = query -> List.of(new TelemetryQueryPort.Point(
            Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("12.5")));
    private final TelemetryAnalyticsService service = new TelemetryAnalyticsService(
            meters, telemetry, new AuthorizationPolicy());

    @Test
    void authorizesMeterAndRendersBucketsInRequestedIanaZone() throws Exception {
        var request = new TelemetryAnalyticsService.QuerySeries(Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"), 300,
                TelemetryQuery.ValueField.INTERVAL_ACCUMULATION, TelemetryQuery.Aggregation.SUM,
                "Asia/Shanghai");

        var series = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.query(operator("analytics:view"), meterId, request));

        assertEquals("Asia/Shanghai", series.zoneId());
        assertEquals("2026-01-01T08:00+08:00", series.points().getFirst().localBucketStart());
        assertEquals("kWh", series.canonicalUnitCode());
    }

    @Test
    void rejectsDeltaForNonCumulativeMeter() {
        Meter instantaneous = Meter.create(UUID.randomUUID(), tenant.value(), UUID.randomUUID(), null,
                UUID.randomUUID(), UUID.randomUUID(), "power", "Power", Meter.Kind.STANDARD,
                null, null, 2, ValueSemantics.INSTANTANEOUS, Instant.parse("2026-01-01T00:00:00Z"));
        var other = new TelemetryAnalyticsService(new StubMeterRepository(instantaneous), telemetry,
                new AuthorizationPolicy());
        var request = new TelemetryAnalyticsService.QuerySeries(Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"), 300,
                TelemetryQuery.ValueField.DELTA, TelemetryQuery.Aggregation.MEAN, null);

        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> other.query(operator("analytics:view"), instantaneous.id(), request)));
    }

    private AuthenticatedPrincipal operator(String permission) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of("/root"), Set.of());
    }

    private static final class StubMeterRepository implements MeterRepository {
        private final Meter meter;
        private StubMeterRepository(Meter meter) { this.meter = meter; }
        public boolean existsByCode(String code) { return false; }
        public Optional<DeviceContext> findDeviceContext(UUID deviceId) { return Optional.empty(); }
        public Optional<ParameterConfiguration> findParameterConfiguration(UUID parameterId, UUID unitId) {
            return Optional.empty();
        }
        public boolean isBoundToDeviceModel(UUID deviceId, UUID parameterId, UUID unitId) { return false; }
        public boolean facilityExists(UUID facilityId) { return false; }
        public void insert(Meter meter, MeterConfigurationChanged event) { }
        public Optional<MeterDetails> findDetails(UUID meterId) {
            return meter.id().equals(meterId)
                    ? Optional.of(new MeterDetails(meter, "/root/site", "kWh", "UTC"))
                    : Optional.empty();
        }
    }
}

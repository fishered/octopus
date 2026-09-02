package com.accuenergy.octopus.mgmt.infrastructure.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.mgmt.domain.analytics.TelemetryQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InfluxTelemetryQueryAdapterTest {
    @Test
    void buildsTenantAndMeterScopedAllowlistedFlux() {
        UUID tenantId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var query = new TelemetryQuery(tenantId, meterId, TelemetryQuery.ValueField.INTERVAL_ACCUMULATION,
                TelemetryQuery.Aggregation.SUM, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), Duration.ofHours(1));

        String flux = InfluxTelemetryQueryAdapter.buildFlux("octopus-telemetry", query);

        assertTrue(flux.contains("r.tenant_id == \"" + tenantId + "\""));
        assertTrue(flux.contains("r.meter_id == \"" + meterId + "\""));
        assertTrue(flux.contains("r._field == \"interval_accumulation\""));
        assertTrue(flux.contains("aggregateWindow(every: 3600s, fn: sum"));
    }
}

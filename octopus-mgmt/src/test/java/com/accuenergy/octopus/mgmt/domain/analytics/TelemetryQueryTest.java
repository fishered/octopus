package com.accuenergy.octopus.mgmt.domain.analytics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelemetryQueryTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsUnboundedQueriesAndRawSums() {
        assertThrows(IllegalArgumentException.class, () -> new TelemetryQuery(UUID.randomUUID(),
                UUID.randomUUID(), TelemetryQuery.ValueField.RAW_VALUE, TelemetryQuery.Aggregation.MEAN,
                START, START.plus(Duration.ofDays(367)), Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryQuery(UUID.randomUUID(),
                UUID.randomUUID(), TelemetryQuery.ValueField.RAW_VALUE, TelemetryQuery.Aggregation.SUM,
                START, START.plus(Duration.ofHours(1)), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new TelemetryQuery(UUID.randomUUID(),
                UUID.randomUUID(), TelemetryQuery.ValueField.DELTA, TelemetryQuery.Aggregation.SUM,
                START, START.plus(Duration.ofDays(2)), Duration.ofSeconds(1)));
    }
}

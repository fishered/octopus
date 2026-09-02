package com.accuenergy.octopus.mgmt.domain.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped, bounded and provider-neutral time-series query. */
public record TelemetryQuery(UUID tenantId, UUID meterId, ValueField field,
        Aggregation aggregation, Instant start, Instant end, Duration bucket) {
    private static final Duration MAX_RANGE = Duration.ofDays(366);
    private static final long MAX_POINTS = 10_000;

    public TelemetryQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(bucket, "bucket");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
        Duration range = Duration.between(start, end);
        if (range.compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("Telemetry range cannot exceed 366 days");
        }
        if (bucket.isNegative() || bucket.isZero() || bucket.getNano() != 0
                || bucket.compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("bucket must be whole seconds between 1 second and 31 days");
        }
        long points = Math.floorDiv(range.toMillis() + bucket.toMillis() - 1, bucket.toMillis());
        if (points > MAX_POINTS) throw new IllegalArgumentException("Telemetry query exceeds 10000 buckets");
        if (field == ValueField.RAW_VALUE && aggregation == Aggregation.SUM) {
            throw new IllegalArgumentException("SUM is not valid for raw readings; query interval accumulation");
        }
    }

    public enum ValueField {
        RAW_VALUE("raw_value"), DELTA("delta"), INTERVAL_ACCUMULATION("interval_accumulation");
        private final String storageField;
        ValueField(String storageField) { this.storageField = storageField; }
        public String storageField() { return storageField; }
    }

    public enum Aggregation {
        MEAN("mean"), SUM("sum"), MIN("min"), MAX("max"), LAST("last");
        private final String fluxFunction;
        Aggregation(String fluxFunction) { this.fluxFunction = fluxFunction; }
        public String fluxFunction() { return fluxFunction; }
    }
}

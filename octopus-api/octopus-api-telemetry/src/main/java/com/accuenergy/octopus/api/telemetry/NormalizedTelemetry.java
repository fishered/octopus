package com.accuenergy.octopus.api.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record NormalizedTelemetry(
        int schemaVersion,
        UUID sourceEventId,
        UUID tenantId,
        UUID deviceId,
        UUID meterId,
        UUID parameterId,
        long modelVersion,
        long algorithmVersion,
        String bootId,
        long sequence,
        Instant occurredAt,
        Instant receivedAt,
        BigDecimal rawValue,
        BigDecimal delta,
        BigDecimal intervalAccumulation,
        String canonicalUnitCode,
        Set<ReadingQuality> quality) {

    public NormalizedTelemetry {
        if (schemaVersion < 1 || schemaVersion > 2) {
            throw new IllegalArgumentException("Unsupported normalized telemetry schemaVersion");
        }
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(parameterId, "parameterId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(rawValue, "rawValue");
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(intervalAccumulation, "intervalAccumulation");
        Objects.requireNonNull(canonicalUnitCode, "canonicalUnitCode");
        if (schemaVersion >= 2 && (bootId == null || bootId.isBlank())) {
            throw new IllegalArgumentException("bootId must not be blank for schemaVersion 2");
        }
        if (bootId == null || bootId.isBlank()) bootId = "legacy-unknown";
        quality = Set.copyOf(quality);
    }

    public String orderingKey() {
        return tenantId + ":" + deviceId + ":" + meterId;
    }
}

package com.accuenergy.octopus.api.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TelemetryReading(
        int schemaVersion,
        UUID eventId,
        UUID tenantId,
        UUID deviceId,
        UUID meterId,
        UUID parameterId,
        long modelVersion,
        String bootId,
        long sequence,
        Instant occurredAt,
        Instant receivedAt,
        BigDecimal value,
        String unitCode,
        ReadingQuality quality) {

    public TelemetryReading {
        if (schemaVersion < 1 || modelVersion < 1 || sequence < 0) {
            throw new IllegalArgumentException("Invalid telemetry version or sequence");
        }
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(parameterId, "parameterId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unitCode, "unitCode");
        Objects.requireNonNull(quality, "quality");
        if (bootId == null || bootId.isBlank()) {
            throw new IllegalArgumentException("bootId must not be blank");
        }
    }

    public String orderingKey() {
        return tenantId + ":" + deviceId + ":" + meterId;
    }
}


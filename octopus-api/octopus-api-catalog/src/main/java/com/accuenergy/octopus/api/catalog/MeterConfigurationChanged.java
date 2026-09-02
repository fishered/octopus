package com.accuenergy.octopus.api.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned, idempotent projection event consumed by telemetry collectors. */
public record MeterConfigurationChanged(
        int contractVersion,
        UUID eventId,
        UUID tenantId,
        UUID meterId,
        long modelVersion,
        UUID parameterId,
        ValueSemantics semantics,
        String sourceUnitCode,
        String canonicalUnitCode,
        BigDecimal scale,
        BigDecimal offset,
        BigDecimal rolloverModulus,
        long configurationVersion,
        long algorithmVersion,
        Instant occurredAt) {

    public MeterConfigurationChanged {
        if (contractVersion != 1) throw new IllegalArgumentException("Unsupported contractVersion");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meterId, "meterId");
        if (modelVersion < 1) throw new IllegalArgumentException("modelVersion must be positive");
        Objects.requireNonNull(parameterId, "parameterId");
        Objects.requireNonNull(semantics, "semantics");
        sourceUnitCode = requireText(sourceUnitCode, "sourceUnitCode");
        canonicalUnitCode = requireText(canonicalUnitCode, "canonicalUnitCode");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(offset, "offset");
        if (configurationVersion < 0) throw new IllegalArgumentException("configurationVersion must be non-negative");
        if (algorithmVersion < 1) throw new IllegalArgumentException("algorithmVersion must be positive");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public enum ValueSemantics { INSTANTANEOUS, CUMULATIVE, INTERVAL_DELTA, NET }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}

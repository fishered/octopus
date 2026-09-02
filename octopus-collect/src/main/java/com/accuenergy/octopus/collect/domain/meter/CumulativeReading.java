package com.accuenergy.octopus.collect.domain.meter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CumulativeReading(String bootId, UUID eventId, long sequence,
                                Instant occurredAt, BigDecimal value) {
    public CumulativeReading {
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (bootId == null || bootId.isBlank()) throw new IllegalArgumentException("bootId is required");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(value, "value");
    }
}

package com.accuenergy.octopus.common.core.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class TimePolicy {
    private final Clock clock;

    public TimePolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Instant now() {
        return clock.instant();
    }

    public static ZoneId requireIanaZone(String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        ZoneId zone = ZoneId.of(zoneId);
        if (!zoneId.contains("/") && !"UTC".equals(zoneId)) {
            throw new IllegalArgumentException("Use an IANA region zone ID or UTC: " + zoneId);
        }
        return zone;
    }
}


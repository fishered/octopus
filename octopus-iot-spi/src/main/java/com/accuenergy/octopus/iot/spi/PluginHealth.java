package com.accuenergy.octopus.iot.spi;

import java.time.Instant;

public record PluginHealth(Status status, Instant checkedAt, String detail) {
    public PluginHealth {
        if (status == null || checkedAt == null) throw new NullPointerException("health fields are required");
        detail = detail == null ? "" : detail.strip();
    }

    public enum Status { STARTING, UP, DEGRADED, DOWN, STOPPED }
}

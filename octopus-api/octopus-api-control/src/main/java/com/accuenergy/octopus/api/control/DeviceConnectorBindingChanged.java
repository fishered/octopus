package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceConnectorBindingChanged(int contractVersion, UUID eventId, UUID tenantId, UUID deviceId,
        String pluginId, String codecId, String status, long bindingVersion, Instant occurredAt) {
    public DeviceConnectorBindingChanged {
        if (contractVersion < 1) throw new IllegalArgumentException("contractVersion must be positive");
        Objects.requireNonNull(eventId, "eventId"); Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        pluginId = requireId(pluginId, "pluginId"); codecId = requireId(codecId, "codecId");
        if (status == null || !(status.equals("ACTIVE") || status.equals("DISABLED"))) throw new IllegalArgumentException("status is invalid");
        if (bindingVersion < 1) throw new IllegalArgumentException("bindingVersion must be positive");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
    public String orderingKey() { return tenantId + ":" + deviceId; }
    private static String requireId(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9._-]{0,63}")) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}

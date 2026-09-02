package com.accuenergy.octopus.iot.spi;

import java.util.Objects;
import java.util.UUID;

/** Versioned routing data projected from management; plugins must not query business databases. */
public record DeviceBinding(UUID tenantId, UUID deviceId, String pluginId, String codecId, long version) {
    public DeviceBinding {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        if (pluginId == null || !pluginId.matches("[a-z][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("pluginId is invalid");
        if (codecId == null || !codecId.matches("[a-z][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("codecId is invalid");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }
}

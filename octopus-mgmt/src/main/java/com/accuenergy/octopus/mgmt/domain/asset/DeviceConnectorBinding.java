package com.accuenergy.octopus.mgmt.domain.asset;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DeviceConnectorBinding {
    public enum Status { ACTIVE, DISABLED }
    private final UUID tenantId; private final UUID deviceId; private final String pluginId; private final String codecId;
    private final String configRef; private final Status status; private final long version; private final Instant createdAt; private final Instant updatedAt;
    private DeviceConnectorBinding(UUID tenantId, UUID deviceId, String pluginId, String codecId, String configRef,
            Status status, long version, Instant createdAt, Instant updatedAt) {
        this.tenantId = Objects.requireNonNull(tenantId); this.deviceId = Objects.requireNonNull(deviceId);
        this.pluginId = requireId(pluginId, "pluginId"); this.codecId = requireId(codecId, "codecId");
        this.configRef = configRef == null || configRef.isBlank() ? null : requireText(configRef, "configRef", 255);
        this.status = Objects.requireNonNull(status); if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version; this.createdAt = Objects.requireNonNull(createdAt); this.updatedAt = Objects.requireNonNull(updatedAt);
    }
    public static DeviceConnectorBinding create(UUID tenantId, UUID deviceId, String pluginId, String codecId, String configRef, Status status, Instant now) {
        return new DeviceConnectorBinding(tenantId, deviceId, pluginId, codecId, configRef, status, 1, now, now);
    }
    public static DeviceConnectorBinding restore(UUID tenantId, UUID deviceId, String pluginId, String codecId, String configRef, Status status, long version, Instant createdAt, Instant updatedAt) {
        return new DeviceConnectorBinding(tenantId, deviceId, pluginId, codecId, configRef, status, version, createdAt, updatedAt);
    }
    public DeviceConnectorBinding next(String pluginId, String codecId, String configRef, Status status, Instant now) {
        return new DeviceConnectorBinding(tenantId, deviceId, pluginId, codecId, configRef, status, version + 1, createdAt, now);
    }
    public UUID tenantId() { return tenantId; } public UUID deviceId() { return deviceId; } public String pluginId() { return pluginId; }
    public String codecId() { return codecId; } public String configRef() { return configRef; } public Status status() { return status; }
    public long version() { return version; } public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; }
    private static String requireId(String value, String name) { if (value == null || !value.matches("[a-z][a-z0-9._-]{0,63}")) throw new IllegalArgumentException(name + " is invalid"); return value; }
    private static String requireText(String value, String name, int max) { if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is invalid"); return value.strip(); }
}

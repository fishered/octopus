package com.accuenergy.octopus.mgmt.domain.monitoring;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-facing projection of the latest versioned device-reported state. */
public final class DeviceShadow {
    private final UUID tenantId;
    private final UUID deviceId;
    private long shadowVersion;
    private String stateJson;
    private Instant reportedAt;
    private Instant receivedAt;
    private Long appliedDesiredVersion;
    private long projectionVersion;

    private DeviceShadow(UUID tenantId, UUID deviceId, long shadowVersion, String stateJson,
            Instant reportedAt, Instant receivedAt, Long appliedDesiredVersion, long projectionVersion) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        if (shadowVersion < 0) throw new IllegalArgumentException("shadowVersion must be non-negative");
        this.shadowVersion = shadowVersion;
        this.stateJson = requireState(stateJson);
        this.reportedAt = Objects.requireNonNull(reportedAt, "reportedAt");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (appliedDesiredVersion != null && appliedDesiredVersion < 1) {
            throw new IllegalArgumentException("appliedDesiredVersion must be positive");
        }
        this.appliedDesiredVersion = appliedDesiredVersion;
        if (projectionVersion < 0) throw new IllegalArgumentException("projectionVersion must be non-negative");
        this.projectionVersion = projectionVersion;
    }

    public static DeviceShadow from(DeviceShadowReported event) {
        return new DeviceShadow(event.tenantId(), event.deviceId(), event.shadowVersion(), event.stateJson(),
                event.reportedAt(), event.receivedAt(), event.appliedDesiredVersion(), 0);
    }

    public static DeviceShadow restore(UUID tenantId, UUID deviceId, long shadowVersion, String stateJson,
            Instant reportedAt, Instant receivedAt, Long appliedDesiredVersion, long projectionVersion) {
        return new DeviceShadow(tenantId, deviceId, shadowVersion, stateJson, reportedAt, receivedAt,
                appliedDesiredVersion, projectionVersion);
    }

    public boolean apply(DeviceShadowReported event) {
        if (!tenantId.equals(event.tenantId()) || !deviceId.equals(event.deviceId())) {
            throw new IllegalArgumentException("Shadow event scope mismatch");
        }
        if (event.shadowVersion() < shadowVersion) return false;
        if (event.shadowVersion() == shadowVersion) {
            if (!stateJson.equals(event.stateJson())
                    || !Objects.equals(appliedDesiredVersion, event.appliedDesiredVersion())) {
                throw new IllegalStateException("Conflicting report for the same shadow version");
            }
            return false;
        }
        shadowVersion = event.shadowVersion();
        stateJson = requireState(event.stateJson());
        reportedAt = event.reportedAt();
        receivedAt = event.receivedAt();
        appliedDesiredVersion = event.appliedDesiredVersion();
        projectionVersion++;
        return true;
    }

    public UUID tenantId() { return tenantId; }
    public UUID deviceId() { return deviceId; }
    public long shadowVersion() { return shadowVersion; }
    public String stateJson() { return stateJson; }
    public Instant reportedAt() { return reportedAt; }
    public Instant receivedAt() { return receivedAt; }
    public java.util.Optional<Long> appliedDesiredVersion() { return java.util.Optional.ofNullable(appliedDesiredVersion); }
    public long projectionVersion() { return projectionVersion; }

    private static String requireState(String value) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 262_144) {
            throw new IllegalArgumentException("stateJson is invalid or exceeds 256 KiB");
        }
        return value;
    }
}

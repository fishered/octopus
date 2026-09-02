package com.accuenergy.octopus.api.control;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Canonical device-reported state after topic identity and JSON validation. */
public record DeviceShadowReported(int schemaVersion, UUID eventId, UUID tenantId, UUID deviceId,
        long shadowVersion, String stateJson, Instant reportedAt, Instant receivedAt,
        Long appliedDesiredVersion) {
    private static final int MAX_STATE_BYTES = 262_144;

    public DeviceShadowReported {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        if (shadowVersion < 0) throw new IllegalArgumentException("shadowVersion must be non-negative");
        if (stateJson == null || stateJson.isBlank()
                || stateJson.getBytes(StandardCharsets.UTF_8).length > MAX_STATE_BYTES) {
            throw new IllegalArgumentException("stateJson is invalid or exceeds 256 KiB");
        }
        reportedAt = Objects.requireNonNull(reportedAt, "reportedAt");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        if (appliedDesiredVersion != null && appliedDesiredVersion < 1) {
            throw new IllegalArgumentException("appliedDesiredVersion must be positive");
        }
    }

    public DeviceShadowReported(int schemaVersion, UUID eventId, UUID tenantId, UUID deviceId,
            long shadowVersion, String stateJson, Instant reportedAt, Instant receivedAt) {
        this(schemaVersion, eventId, tenantId, deviceId, shadowVersion, stateJson, reportedAt, receivedAt, null);
    }

    public String orderingKey() { return tenantId + ":" + deviceId; }
}

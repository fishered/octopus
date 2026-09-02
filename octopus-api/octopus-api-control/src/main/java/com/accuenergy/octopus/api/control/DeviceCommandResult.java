package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Signed device-to-platform result carried on the command result MQTT topic. */
public record DeviceCommandResult(int schemaVersion, UUID commandId, UUID tenantId, UUID deviceId,
        Status status, String failureCode, Instant occurredAt) {
    public DeviceCommandResult {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        failureCode = failureCode == null || failureCode.isBlank() ? null : failureCode.strip();
        if (status == Status.FAILED && failureCode == null) {
            throw new IllegalArgumentException("FAILED result requires failureCode");
        }
    }

    public enum Status { ACKNOWLEDGED, SUCCEEDED, FAILED }
}

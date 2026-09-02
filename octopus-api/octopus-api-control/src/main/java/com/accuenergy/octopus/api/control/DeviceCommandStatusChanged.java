package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceCommandStatusChanged(int schemaVersion, UUID eventId, UUID commandId, UUID tenantId,
        UUID deviceId, Status status, String failureCode, Instant occurredAt) {
    public DeviceCommandStatusChanged {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        failureCode = failureCode == null || failureCode.isBlank() ? null : failureCode.strip();
        if (status == Status.FAILED && failureCode == null) {
            throw new IllegalArgumentException("FAILED status requires failureCode");
        }
    }

    public String orderingKey() { return tenantId + ":" + deviceId; }
    public enum Status { ACCEPTED, DISPATCHED, ACKNOWLEDGED, SUCCEEDED, FAILED, EXPIRED }
}

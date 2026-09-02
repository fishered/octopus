package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceCommandRequested(int schemaVersion, UUID eventId, UUID commandId, UUID tenantId,
        UUID deviceId, UUID requestedBy, String idempotencyKey, String operation, byte[] payload,
        Instant requestedAt, Instant expiresAt) {
    public DeviceCommandRequested {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 128);
        operation = requireText(operation, "operation", 64);
        if (!operation.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("operation is invalid");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length > 65_536) throw new IllegalArgumentException("payload exceeds 64 KiB");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must follow requestedAt");
    }

    @Override public byte[] payload() { return payload.clone(); }
    public String orderingKey() { return tenantId + ":" + deviceId; }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

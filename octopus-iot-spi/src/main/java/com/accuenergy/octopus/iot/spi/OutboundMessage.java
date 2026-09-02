package com.accuenergy.octopus.iot.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral message that a transport plugin publishes to a device. */
public record OutboundMessage(MessageKind kind, UUID tenantId, UUID deviceId, UUID correlationId,
        String operation, byte[] payload, Instant requestedAt, Instant expiresAt, String contentType) {
    public OutboundMessage {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (operation == null || !operation.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("operation is invalid");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length > 65_536) throw new IllegalArgumentException("payload exceeds 64 KiB");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must follow requestedAt");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.strip();
    }

    @Override public byte[] payload() { return payload.clone(); }
}

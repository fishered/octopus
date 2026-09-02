package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral command envelope delivered to a device. Devices de-duplicate by commandId. */
public record DeviceCommandEnvelope(int schemaVersion, UUID commandId, UUID tenantId, UUID deviceId,
        String operation, byte[] payload, Instant requestedAt, Instant expiresAt) {
    public DeviceCommandEnvelope {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        if (operation == null || !operation.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("operation is invalid");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length > 65_536) throw new IllegalArgumentException("payload exceeds 64 KiB");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must follow requestedAt");
    }

    @Override public byte[] payload() { return payload.clone(); }
}

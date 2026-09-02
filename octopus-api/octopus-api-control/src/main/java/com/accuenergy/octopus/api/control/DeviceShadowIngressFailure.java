package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceShadowIngressFailure(int schemaVersion, UUID eventId, String sourceTopic,
        byte[] payload, String reason, Instant receivedAt) {
    public DeviceShadowIngressFailure {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(eventId, "eventId");
        if (sourceTopic == null || sourceTopic.isBlank()) throw new IllegalArgumentException("sourceTopic is required");
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    @Override public byte[] payload() { return payload.clone(); }
}

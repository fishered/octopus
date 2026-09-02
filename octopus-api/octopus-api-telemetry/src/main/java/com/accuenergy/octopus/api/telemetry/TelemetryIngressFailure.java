package com.accuenergy.octopus.api.telemetry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable quarantine envelope for malformed or identity-mismatched MQTT uplinks. */
public record TelemetryIngressFailure(
        int schemaVersion,
        UUID failureId,
        String sourceTopic,
        byte[] payload,
        String reason,
        Instant receivedAt) {

    public TelemetryIngressFailure {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported schemaVersion");
        Objects.requireNonNull(failureId, "failureId");
        sourceTopic = requireText(sourceTopic, "sourceTopic", 1000);
        payload = Objects.requireNonNull(payload, "payload").clone();
        reason = requireText(reason, "reason", 1000);
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    @Override
    public byte[] payload() { return payload.clone(); }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}

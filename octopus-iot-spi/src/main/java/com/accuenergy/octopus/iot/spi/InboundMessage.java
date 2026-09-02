package com.accuenergy.octopus.iot.spi;

import java.time.Instant;
import java.util.Objects;

/** Raw message delivered by a transport before protocol decoding. */
public record InboundMessage(String transport, String topic, byte[] payload, int qos,
        boolean retained, Instant receivedAt, String contentType) {
    public InboundMessage {
        requireText(transport, "transport");
        requireText(topic, "topic");
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length > 1_048_576) throw new IllegalArgumentException("payload exceeds 1 MiB");
        if (qos < 0 || qos > 2) throw new IllegalArgumentException("qos is invalid");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.strip();
    }

    @Override public byte[] payload() { return payload.clone(); }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}

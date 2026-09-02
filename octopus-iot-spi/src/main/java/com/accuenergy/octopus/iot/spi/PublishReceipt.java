package com.accuenergy.octopus.iot.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublishReceipt(UUID correlationId, Status status, Instant acceptedAt, String providerReceipt) {
    public PublishReceipt {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        providerReceipt = providerReceipt == null || providerReceipt.isBlank() ? null : providerReceipt.strip();
    }

    public enum Status { ACCEPTED, REJECTED, EXPIRED }
}

package com.accuenergy.octopus.ca.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Verified device identity used by broker authorization; no client-supplied identity is trusted. */
public record DeviceCertificateAuthorization(UUID tenantId, UUID deviceId, String certificateSerial,
        Instant notAfter) {
    public DeviceCertificateAuthorization {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        if (certificateSerial == null || certificateSerial.isBlank()) {
            throw new IllegalArgumentException("certificateSerial is required");
        }
        Objects.requireNonNull(notAfter, "notAfter");
    }
}

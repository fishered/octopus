package com.accuenergy.octopus.ca.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Device trust identity; private keys never enter this aggregate or the service database. */
public final class DeviceIdentity {
    private final UUID identityId;
    private final String hardwareSerial;
    private final String manufacturer;
    private final String modelCode;
    private final String batchCode;
    private final String bootstrapPublicKeyFingerprint;
    private Status status;
    private UUID tenantId;
    private String operationalCertificateSerial;
    private Instant certificateExpiresAt;
    private Instant claimedAt;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private DeviceIdentity(UUID identityId, String hardwareSerial, String manufacturer,
                           String modelCode, String batchCode, String bootstrapPublicKeyFingerprint,
                           Status status, UUID tenantId, String operationalCertificateSerial,
                           Instant certificateExpiresAt, Instant claimedAt, long version,
                           Instant createdAt, Instant updatedAt) {
        this.identityId = Objects.requireNonNull(identityId, "identityId");
        this.hardwareSerial = requireText(hardwareSerial, "hardwareSerial", 200);
        this.manufacturer = requireText(manufacturer, "manufacturer", 200);
        this.modelCode = requireText(modelCode, "modelCode", 128);
        this.batchCode = requireText(batchCode, "batchCode", 128);
        this.bootstrapPublicKeyFingerprint = requireText(bootstrapPublicKeyFingerprint,
                "bootstrapPublicKeyFingerprint", 128);
        this.status = Objects.requireNonNull(status, "status");
        this.tenantId = tenantId;
        this.operationalCertificateSerial = normalize(operationalCertificateSerial);
        this.certificateExpiresAt = certificateExpiresAt;
        this.claimedAt = claimedAt;
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        validateState();
    }

    public static DeviceIdentity manufacture(UUID identityId, String hardwareSerial, String manufacturer,
                                             String modelCode, String batchCode,
                                             String bootstrapPublicKeyFingerprint, Instant now) {
        return new DeviceIdentity(identityId, hardwareSerial, manufacturer, modelCode, batchCode,
                bootstrapPublicKeyFingerprint, Status.MANUFACTURED, null, null, null, null,
                0, now, now);
    }

    public static DeviceIdentity restore(UUID identityId, String hardwareSerial, String manufacturer,
                                         String modelCode, String batchCode,
                                         String bootstrapPublicKeyFingerprint, Status status, UUID tenantId,
                                         String operationalCertificateSerial, Instant certificateExpiresAt,
                                         Instant claimedAt, long version, Instant createdAt, Instant updatedAt) {
        return new DeviceIdentity(identityId, hardwareSerial, manufacturer, modelCode, batchCode,
                bootstrapPublicKeyFingerprint, status, tenantId, operationalCertificateSerial,
                certificateExpiresAt, claimedAt, version, createdAt, updatedAt);
    }

    public void enableBootstrap(Instant now) {
        require(Status.MANUFACTURED);
        status = Status.BOOTSTRAP_READY;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void claim(UUID newTenantId, Instant now) {
        require(Status.BOOTSTRAP_READY);
        tenantId = Objects.requireNonNull(newTenantId, "newTenantId");
        claimedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        status = Status.CLAIMED;
    }

    public void activate(String certificateSerial, Instant expiresAt, Instant now) {
        if (status != Status.CLAIMED && status != Status.ACTIVE) {
            throw new IllegalStateException("Identity must be claimed or active to issue a certificate");
        }
        operationalCertificateSerial = requireText(certificateSerial, "certificateSerial", 200);
        if (!Objects.requireNonNull(expiresAt, "expiresAt").isAfter(Objects.requireNonNull(now, "now"))) {
            throw new IllegalArgumentException("Certificate must expire in the future");
        }
        certificateExpiresAt = expiresAt;
        status = Status.ACTIVE;
        updatedAt = now;
    }

    public void revoke(Instant now) {
        if (status == Status.REVOKED || status == Status.DECOMMISSIONED) return;
        if (status != Status.CLAIMED && status != Status.ACTIVE) {
            throw new IllegalStateException("Only claimed or active identities can be revoked");
        }
        status = Status.REVOKED;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void decommission(Instant now) {
        if (status != Status.ACTIVE && status != Status.REVOKED) {
            throw new IllegalStateException("Only active or revoked identities can be decommissioned");
        }
        status = Status.DECOMMISSIONED;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID identityId() { return identityId; }
    public String hardwareSerial() { return hardwareSerial; }
    public String manufacturer() { return manufacturer; }
    public String modelCode() { return modelCode; }
    public String batchCode() { return batchCode; }
    public String bootstrapPublicKeyFingerprint() { return bootstrapPublicKeyFingerprint; }
    public Status status() { return status; }
    public Optional<UUID> tenantId() { return Optional.ofNullable(tenantId); }
    public Optional<String> operationalCertificateSerial() { return Optional.ofNullable(operationalCertificateSerial); }
    public Optional<Instant> certificateExpiresAt() { return Optional.ofNullable(certificateExpiresAt); }
    public Optional<Instant> claimedAt() { return Optional.ofNullable(claimedAt); }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private void validateState() {
        boolean claimed = tenantId != null && claimedAt != null;
        if ((status == Status.CLAIMED || status == Status.ACTIVE || status == Status.REVOKED
                || status == Status.DECOMMISSIONED) && !claimed) {
            throw new IllegalArgumentException("Claimed lifecycle state requires tenant and claimedAt");
        }
        if (status == Status.ACTIVE && (operationalCertificateSerial == null || certificateExpiresAt == null)) {
            throw new IllegalArgumentException("Active identity requires an operational certificate");
        }
    }

    private void require(Status expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    public enum Status { MANUFACTURED, BOOTSTRAP_READY, CLAIMED, ACTIVE, REVOKED, DECOMMISSIONED }
}

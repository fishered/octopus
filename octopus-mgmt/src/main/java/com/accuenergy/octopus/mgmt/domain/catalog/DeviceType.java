package com.accuenergy.octopus.mgmt.domain.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Protocol and capability classification shared by compatible device models. */
public final class DeviceType {
    private final UUID id;
    private final Optional<UUID> tenantId;
    private final String code;
    private final String displayName;
    private final String capabilitiesDocument;
    private final Status status;
    private final Instant createdAt;

    private DeviceType(UUID id, Optional<UUID> tenantId, String code, String displayName,
                       String capabilitiesDocument, Status status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.code = requireText(code, "code", 64);
        this.displayName = requireText(displayName, "displayName", 200);
        this.capabilitiesDocument = requireText(capabilitiesDocument, "capabilitiesDocument", 1_000_000);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DeviceType createTenant(UUID id, UUID tenantId, String code, String displayName,
                                          String capabilitiesDocument, Instant now) {
        return new DeviceType(id, Optional.of(Objects.requireNonNull(tenantId, "tenantId")), code,
                displayName, capabilitiesDocument, Status.ACTIVE, now);
    }

    public static DeviceType restore(UUID id, UUID tenantId, String code, String displayName,
                                     String capabilitiesDocument, Status status, Instant createdAt) {
        return new DeviceType(id, Optional.ofNullable(tenantId), code, displayName, capabilitiesDocument,
                status, createdAt);
    }

    public UUID id() { return id; }
    public Optional<UUID> tenantId() { return tenantId; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public String capabilitiesDocument() { return capabilitiesDocument; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }

    public enum Status { ACTIVE, RETIRED }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

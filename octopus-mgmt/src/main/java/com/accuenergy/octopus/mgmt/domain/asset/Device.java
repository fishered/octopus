package com.accuenergy.octopus.mgmt.domain.asset;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Device {
    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private UUID facilityId;
    private final UUID deviceTypeId;
    private UUID thingModelId;
    private long modelVersion;
    private final String code;
    private String displayName;
    private Status status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Device(UUID id, UUID tenantId, UUID organizationId, UUID facilityId, UUID deviceTypeId,
                   UUID thingModelId, long modelVersion, String code, String displayName,
                   Status status, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.facilityId = facilityId;
        this.deviceTypeId = Objects.requireNonNull(deviceTypeId, "deviceTypeId");
        this.thingModelId = Objects.requireNonNull(thingModelId, "thingModelId");
        if (modelVersion < 1) throw new IllegalArgumentException("modelVersion must be positive");
        this.modelVersion = modelVersion;
        this.code = requireText(code, "code", 128);
        this.displayName = requireText(displayName, "displayName", 200);
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Device register(UUID id, UUID tenantId, UUID organizationId, UUID facilityId,
                                  UUID deviceTypeId, UUID thingModelId, long modelVersion,
                                  String code, String displayName, Instant now) {
        return new Device(id, tenantId, organizationId, facilityId, deviceTypeId, thingModelId,
                modelVersion, code, displayName, Status.ACTIVE, 0, now, now);
    }

    public static Device restore(UUID id, UUID tenantId, UUID organizationId, UUID facilityId,
                                 UUID deviceTypeId, UUID thingModelId, long modelVersion, String code,
                                 String displayName, Status status, long version, Instant createdAt, Instant updatedAt) {
        return new Device(id, tenantId, organizationId, facilityId, deviceTypeId, thingModelId,
                modelVersion, code, displayName, status, version, createdAt, updatedAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public Optional<UUID> facilityId() { return Optional.ofNullable(facilityId); }
    public UUID deviceTypeId() { return deviceTypeId; }
    public UUID thingModelId() { return thingModelId; }
    public long modelVersion() { return modelVersion; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " is invalid");
        return value.strip();
    }
    public enum Status { ACTIVE, SUSPENDED, DECOMMISSIONED }
}


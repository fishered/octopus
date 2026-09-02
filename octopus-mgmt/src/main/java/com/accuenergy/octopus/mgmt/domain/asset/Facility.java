package com.accuenergy.octopus.mgmt.domain.asset;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Tenant-owned physical or logical place. Geometry is represented as GeoJSON at the domain boundary. */
public final class Facility {
    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID parentId;
    private final String code;
    private final String displayName;
    private final String facilityType;
    private final String zoneId;
    private final String geometryGeoJson;
    private Status status;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Facility(UUID id, UUID tenantId, UUID organizationId, UUID parentId, String code,
                     String displayName, String facilityType, String zoneId, String geometryGeoJson,
                     Status status, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.parentId = parentId;
        this.code = requireText(code, "code", 64);
        this.displayName = requireText(displayName, "displayName", 200);
        this.facilityType = requireText(facilityType, "facilityType", 64);
        this.zoneId = normalizeZoneId(zoneId);
        this.geometryGeoJson = normalizeOptional(geometryGeoJson, "geometryGeoJson", 1_000_000);
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Facility create(UUID id, UUID tenantId, UUID organizationId, UUID parentId,
                                  String code, String displayName, String facilityType, String zoneId,
                                  String geometryGeoJson, Instant now) {
        return new Facility(id, tenantId, organizationId, parentId, code, displayName, facilityType,
                zoneId, geometryGeoJson, Status.ACTIVE, 0, now, now);
    }

    public static Facility restore(UUID id, UUID tenantId, UUID organizationId, UUID parentId,
                                   String code, String displayName, String facilityType, String zoneId,
                                   String geometryGeoJson, Status status, long version,
                                   Instant createdAt, Instant updatedAt) {
        return new Facility(id, tenantId, organizationId, parentId, code, displayName, facilityType,
                zoneId, geometryGeoJson, status, version, createdAt, updatedAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public Optional<UUID> parentId() { return Optional.ofNullable(parentId); }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public String facilityType() { return facilityType; }
    public Optional<String> zoneId() { return Optional.ofNullable(zoneId); }
    public Optional<String> geometryGeoJson() { return Optional.ofNullable(geometryGeoJson); }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum Status { ACTIVE, SUSPENDED, RETIRED }

    private static String normalizeZoneId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = requireText(value, "zoneId", 64);
        ZoneId.of(normalized);
        if (!ZoneId.getAvailableZoneIds().contains(normalized)) {
            throw new IllegalArgumentException("zoneId must be an IANA region ID");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, name, maximumLength);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

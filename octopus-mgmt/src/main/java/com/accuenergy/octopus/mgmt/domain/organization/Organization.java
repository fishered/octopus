package com.accuenergy.octopus.mgmt.domain.organization;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Organization {
    private final UUID id;
    private final UUID tenantId;
    private final UUID parentId;
    private final String path;
    private final String code;
    private final String displayName;
    private final String zoneId;
    private final Status status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Organization(UUID id, UUID tenantId, UUID parentId, String path, String code,
                         String displayName, String zoneId, Status status, long version,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.parentId = parentId;
        this.path = requireText(path, "path", 2000);
        this.code = requireCode(code);
        this.displayName = requireText(displayName, "displayName", 200);
        this.zoneId = validateZone(zoneId);
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        String expectedSuffix = "/" + id;
        if (!path.endsWith(expectedSuffix)) throw new IllegalArgumentException("Organization path must end with its ID");
    }

    public static Organization createRoot(UUID id, UUID tenantId, String code, String displayName,
                                          String zoneId, Instant now) {
        return new Organization(id, tenantId, null, "/" + id, code, displayName, zoneId,
                Status.ACTIVE, 0, now, now);
    }

    public static Organization createChild(UUID id, UUID tenantId, UUID parentId, String parentPath,
                                           String code, String displayName, String zoneId, Instant now) {
        String normalizedParent = requireText(parentPath, "parentPath", 1900);
        return new Organization(id, tenantId, Objects.requireNonNull(parentId, "parentId"),
                normalizedParent + "/" + id, code, displayName, zoneId, Status.ACTIVE, 0, now, now);
    }

    public static Organization restore(UUID id, UUID tenantId, UUID parentId, String path, String code,
                                       String displayName, String zoneId, Status status, long version,
                                       Instant createdAt, Instant updatedAt) {
        return new Organization(id, tenantId, parentId, path, code, displayName, zoneId,
                status, version, createdAt, updatedAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public Optional<UUID> parentId() { return Optional.ofNullable(parentId); }
    public String path() { return path; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public Optional<String> zoneId() { return Optional.ofNullable(zoneId); }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum Status { ACTIVE, SUSPENDED, RETIRED }

    private static String requireCode(String value) {
        String code = requireText(value, "code", 64);
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("code contains unsupported characters");
        }
        return code;
    }

    private static String validateZone(String value) {
        if (value == null || value.isBlank()) return null;
        String zone = requireText(value, "zoneId", 64);
        ZoneId.of(zone);
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new IllegalArgumentException("zoneId must be an IANA region ID");
        }
        return zone;
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

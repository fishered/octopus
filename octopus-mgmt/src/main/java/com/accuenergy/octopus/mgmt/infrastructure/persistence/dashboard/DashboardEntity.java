package com.accuenergy.octopus.mgmt.infrastructure.persistence.dashboard;

import java.time.Instant;
import java.util.UUID;

public final class DashboardEntity {
    private UUID id;
    private UUID tenantId;
    private UUID organizationId;
    private UUID ownerAccountId;
    private String code;
    private String displayName;
    private String description;
    private String defaultZoneId;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getOrganizationId() { return organizationId; } public void setOrganizationId(UUID value) { organizationId = value; }
    public UUID getOwnerAccountId() { return ownerAccountId; } public void setOwnerAccountId(UUID value) { ownerAccountId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public String getDefaultZoneId() { return defaultZoneId; } public void setDefaultZoneId(String value) { defaultZoneId = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public long getVersion() { return version; } public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import java.time.Instant;
import java.util.UUID;

public class ThingModelEntity {
    private UUID id;
    private UUID tenantId;
    private UUID deviceTypeId;
    private String code;
    private String displayName;
    private long modelVersion;
    private String schemaDocument;
    private String status;
    private Instant publishedAt;
    private Instant createdAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getDeviceTypeId() { return deviceTypeId; } public void setDeviceTypeId(UUID value) { deviceTypeId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public long getModelVersion() { return modelVersion; } public void setModelVersion(long value) { modelVersion = value; }
    public String getSchemaDocument() { return schemaDocument; } public void setSchemaDocument(String value) { schemaDocument = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public Instant getPublishedAt() { return publishedAt; } public void setPublishedAt(Instant value) { publishedAt = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}

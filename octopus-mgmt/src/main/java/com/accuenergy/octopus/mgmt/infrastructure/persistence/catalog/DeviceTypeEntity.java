package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import java.time.Instant;
import java.util.UUID;

public class DeviceTypeEntity {
    private UUID id;
    private UUID tenantId;
    private String code;
    private String displayName;
    private String capabilitiesDocument;
    private String status;
    private Instant createdAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getCapabilitiesDocument() { return capabilitiesDocument; }
    public void setCapabilitiesDocument(String value) { capabilitiesDocument = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}

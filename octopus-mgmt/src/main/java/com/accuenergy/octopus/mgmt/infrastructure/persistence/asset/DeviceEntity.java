package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import java.util.UUID;

@TableName("device")
public class DeviceEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private UUID organizationId;
    private UUID facilityId;
    private UUID deviceTypeId;
    private UUID thingModelId;
    private long modelVersion;
    private String code;
    private String displayName;
    private String status;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID v) { id = v; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId = v; }
    public UUID getOrganizationId() { return organizationId; } public void setOrganizationId(UUID v) { organizationId = v; }
    public UUID getFacilityId() { return facilityId; } public void setFacilityId(UUID v) { facilityId = v; }
    public UUID getDeviceTypeId() { return deviceTypeId; } public void setDeviceTypeId(UUID v) { deviceTypeId = v; }
    public UUID getThingModelId() { return thingModelId; } public void setThingModelId(UUID v) { thingModelId = v; }
    public long getModelVersion() { return modelVersion; } public void setModelVersion(long v) { modelVersion = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String v) { displayName = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public long getVersion() { return version; } public void setVersion(long v) { version = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}


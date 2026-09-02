package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import java.util.UUID;

@TableName("facility")
public class FacilityEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private UUID organizationId;
    private UUID parentId;
    private String code;
    private String displayName;
    private String facilityType;
    private String zoneId;
    @TableField(exist = false) private String geometryGeoJson;
    private String status;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getOrganizationId() { return organizationId; } public void setOrganizationId(UUID value) { organizationId = value; }
    public UUID getParentId() { return parentId; } public void setParentId(UUID value) { parentId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getFacilityType() { return facilityType; } public void setFacilityType(String value) { facilityType = value; }
    public String getZoneId() { return zoneId; } public void setZoneId(String value) { zoneId = value; }
    public String getGeometryGeoJson() { return geometryGeoJson; } public void setGeometryGeoJson(String value) { geometryGeoJson = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public long getVersion() { return version; } public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}

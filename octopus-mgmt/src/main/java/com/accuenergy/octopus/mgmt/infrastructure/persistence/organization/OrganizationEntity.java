package com.accuenergy.octopus.mgmt.infrastructure.persistence.organization;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import java.util.UUID;

@TableName("organization")
public class OrganizationEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private UUID parentId;
    private String path;
    private String code;
    private String displayName;
    private String zoneId;
    private String status;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getParentId() { return parentId; } public void setParentId(UUID value) { parentId = value; }
    public String getPath() { return path; } public void setPath(String value) { path = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getZoneId() { return zoneId; } public void setZoneId(String value) { zoneId = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public long getVersion() { return version; } public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}

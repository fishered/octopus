package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("device_connector_binding")
public class DeviceConnectorBindingEntity {
    @TableId private UUID deviceId;
    private UUID tenantId; private String pluginId; private String codecId; private String configRef; private String status;
    private long version; private Instant createdAt; private Instant updatedAt;
    public UUID getDeviceId() { return deviceId; } public void setDeviceId(UUID v) { deviceId=v; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public String getPluginId() { return pluginId; } public void setPluginId(String v) { pluginId=v; }
    public String getCodecId() { return codecId; } public void setCodecId(String v) { codecId=v; }
    public String getConfigRef() { return configRef; } public void setConfigRef(String v) { configRef=v; }
    public String getStatus() { return status; } public void setStatus(String v) { status=v; }
    public long getVersion() { return version; } public void setVersion(long v) { version=v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt=v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt=v; }
}

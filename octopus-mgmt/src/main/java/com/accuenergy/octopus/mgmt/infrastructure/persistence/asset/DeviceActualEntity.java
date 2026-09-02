package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import java.util.UUID;

@TableName("device_actual")
public class DeviceActualEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private UUID deviceId;
    private String hardwareSerial;
    private String manufacturer;
    private String firmwareVersion;
    private UUID certificateId;
    private String connectivityStatus;
    private Instant lastSeenAt;
    private Instant commissionedAt;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getDeviceId() { return deviceId; } public void setDeviceId(UUID value) { deviceId = value; }
    public String getHardwareSerial() { return hardwareSerial; } public void setHardwareSerial(String value) { hardwareSerial = value; }
    public String getManufacturer() { return manufacturer; } public void setManufacturer(String value) { manufacturer = value; }
    public String getFirmwareVersion() { return firmwareVersion; } public void setFirmwareVersion(String value) { firmwareVersion = value; }
    public UUID getCertificateId() { return certificateId; } public void setCertificateId(UUID value) { certificateId = value; }
    public String getConnectivityStatus() { return connectivityStatus; } public void setConnectivityStatus(String value) { connectivityStatus = value; }
    public Instant getLastSeenAt() { return lastSeenAt; } public void setLastSeenAt(Instant value) { lastSeenAt = value; }
    public Instant getCommissionedAt() { return commissionedAt; } public void setCommissionedAt(Instant value) { commissionedAt = value; }
    public long getVersion() { return version; } public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}

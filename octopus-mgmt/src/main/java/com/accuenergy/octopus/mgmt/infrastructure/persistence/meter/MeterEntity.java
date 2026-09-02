package com.accuenergy.octopus.mgmt.infrastructure.persistence.meter;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("meter")
public class MeterEntity {
    @TableId private UUID id;
    private UUID tenantId;
    private UUID deviceId;
    private UUID facilityId;
    private UUID parameterId;
    private UUID unitId;
    private String code;
    private String displayName;
    private String meterKind;
    private String calculationExpression;
    private BigDecimal rolloverModulus;
    private int decimalScale;
    private String status;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID value) { tenantId = value; }
    public UUID getDeviceId() { return deviceId; } public void setDeviceId(UUID value) { deviceId = value; }
    public UUID getFacilityId() { return facilityId; } public void setFacilityId(UUID value) { facilityId = value; }
    public UUID getParameterId() { return parameterId; } public void setParameterId(UUID value) { parameterId = value; }
    public UUID getUnitId() { return unitId; } public void setUnitId(UUID value) { unitId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public String getMeterKind() { return meterKind; } public void setMeterKind(String value) { meterKind = value; }
    public String getCalculationExpression() { return calculationExpression; } public void setCalculationExpression(String value) { calculationExpression = value; }
    public BigDecimal getRolloverModulus() { return rolloverModulus; } public void setRolloverModulus(BigDecimal value) { rolloverModulus = value; }
    public int getDecimalScale() { return decimalScale; } public void setDecimalScale(int value) { decimalScale = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public long getVersion() { return version; } public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}

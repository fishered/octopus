package com.accuenergy.octopus.mgmt.infrastructure.persistence.alarm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlarmRuleEntity {
    private UUID id;
    private UUID tenantId;
    private UUID organizationId;
    private UUID deviceId;
    private UUID meterId;
    private UUID parameterId;
    private String code;
    private String displayName;
    private String valueSelector;
    private String comparison;
    private BigDecimal triggerThreshold;
    private BigDecimal clearThreshold;
    private String severity;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getMeterId() { return meterId; }
    public void setMeterId(UUID meterId) { this.meterId = meterId; }
    public UUID getParameterId() { return parameterId; }
    public void setParameterId(UUID parameterId) { this.parameterId = parameterId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getValueSelector() { return valueSelector; }
    public void setValueSelector(String valueSelector) { this.valueSelector = valueSelector; }
    public String getComparison() { return comparison; }
    public void setComparison(String comparison) { this.comparison = comparison; }
    public BigDecimal getTriggerThreshold() { return triggerThreshold; }
    public void setTriggerThreshold(BigDecimal triggerThreshold) { this.triggerThreshold = triggerThreshold; }
    public BigDecimal getClearThreshold() { return clearThreshold; }
    public void setClearThreshold(BigDecimal clearThreshold) { this.clearThreshold = clearThreshold; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

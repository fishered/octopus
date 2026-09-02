package com.accuenergy.octopus.mgmt.infrastructure.persistence.alarm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlarmIncidentEntity {
    private UUID id;
    private UUID tenantId;
    private UUID ruleId;
    private UUID organizationId;
    private UUID deviceId;
    private UUID meterId;
    private UUID parameterId;
    private String severity;
    private String state;
    private BigDecimal triggerValue;
    private BigDecimal latestValue;
    private long occurrenceCount;
    private Instant openedAt;
    private Instant lastObservedAt;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant clearedAt;
    private long version;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getMeterId() { return meterId; }
    public void setMeterId(UUID meterId) { this.meterId = meterId; }
    public UUID getParameterId() { return parameterId; }
    public void setParameterId(UUID parameterId) { this.parameterId = parameterId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public BigDecimal getTriggerValue() { return triggerValue; }
    public void setTriggerValue(BigDecimal triggerValue) { this.triggerValue = triggerValue; }
    public BigDecimal getLatestValue() { return latestValue; }
    public void setLatestValue(BigDecimal latestValue) { this.latestValue = latestValue; }
    public long getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getClearedAt() { return clearedAt; }
    public void setClearedAt(Instant clearedAt) { this.clearedAt = clearedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

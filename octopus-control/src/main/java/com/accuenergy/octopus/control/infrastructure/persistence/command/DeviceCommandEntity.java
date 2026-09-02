package com.accuenergy.octopus.control.infrastructure.persistence.command;

import java.time.Instant;
import java.util.UUID;

public final class DeviceCommandEntity {
    private UUID commandId;
    private UUID tenantId;
    private UUID deviceId;
    private UUID requestedBy;
    private String operation;
    private byte[] payload;
    private String status;
    private String failureCode;
    private Instant requestedAt;
    private Instant expiresAt;
    private Instant dispatchedAt;
    private Instant acknowledgedAt;
    private Instant completedAt;
    private long version;

    public UUID getCommandId() { return commandId; }
    public void setCommandId(UUID commandId) { this.commandId = commandId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public byte[] getPayload() { return payload == null ? null : payload.clone(); }
    public void setPayload(byte[] payload) { this.payload = payload == null ? null : payload.clone(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}

package com.accuenergy.octopus.mgmt.domain.control;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Tenant-facing command request and status projection; execution is owned by octopus-control. */
public final class DeviceCommandRequest {
    private final UUID commandId;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID deviceId;
    private final UUID requestedBy;
    private final String idempotencyKey;
    private final String operation;
    private final String payloadSha256;
    private final Instant requestedAt;
    private final Instant expiresAt;
    private DeviceCommandStatusChanged.Status status;
    private String failureCode;
    private Instant dispatchedAt;
    private Instant acknowledgedAt;
    private Instant completedAt;
    private Instant statusUpdatedAt;
    private long version;

    private DeviceCommandRequest(UUID commandId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, String operation, String payloadSha256,
            DeviceCommandStatusChanged.Status status, String failureCode, Instant requestedAt,
            Instant expiresAt, Instant dispatchedAt, Instant acknowledgedAt, Instant completedAt,
            Instant statusUpdatedAt, long version) {
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 128);
        this.operation = requireText(operation, "operation", 64);
        this.payloadSha256 = requireDigest(payloadSha256);
        this.status = Objects.requireNonNull(status, "status");
        this.failureCode = normalize(failureCode);
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must follow requestedAt");
        this.dispatchedAt = dispatchedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.completedAt = completedAt;
        this.statusUpdatedAt = Objects.requireNonNull(statusUpdatedAt, "statusUpdatedAt");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
    }

    public static DeviceCommandRequest create(UUID commandId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, String operation, String payloadSha256,
            Instant requestedAt, Instant expiresAt) {
        return new DeviceCommandRequest(commandId, tenantId, organizationId, deviceId, requestedBy,
                idempotencyKey, operation, payloadSha256, DeviceCommandStatusChanged.Status.ACCEPTED, null,
                requestedAt, expiresAt, null, null, null, requestedAt, 0);
    }

    public static DeviceCommandRequest restore(UUID commandId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, String operation, String payloadSha256,
            DeviceCommandStatusChanged.Status status, String failureCode, Instant requestedAt,
            Instant expiresAt, Instant dispatchedAt, Instant acknowledgedAt, Instant completedAt,
            Instant statusUpdatedAt, long version) {
        return new DeviceCommandRequest(commandId, tenantId, organizationId, deviceId, requestedBy,
                idempotencyKey, operation, payloadSha256, status, failureCode, requestedAt, expiresAt,
                dispatchedAt, acknowledgedAt, completedAt, statusUpdatedAt, version);
    }

    public boolean apply(DeviceCommandStatusChanged event) {
        if (!commandId.equals(event.commandId()) || !tenantId.equals(event.tenantId())
                || !deviceId.equals(event.deviceId())) {
            throw new IllegalArgumentException("Command status event scope does not match request");
        }
        if (event.occurredAt().isBefore(statusUpdatedAt)) return false;
        if (status == event.status()) return false;
        if (!mayTransition(status, event.status())) {
            throw new IllegalStateException("Invalid command status transition " + status + " -> " + event.status());
        }
        status = event.status();
        failureCode = event.failureCode();
        switch (status) {
            case DISPATCHED -> dispatchedAt = event.occurredAt();
            case ACKNOWLEDGED -> acknowledgedAt = event.occurredAt();
            case SUCCEEDED, FAILED, EXPIRED -> completedAt = event.occurredAt();
            case ACCEPTED -> { }
        }
        statusUpdatedAt = event.occurredAt();
        version++;
        return true;
    }

    public boolean matches(UUID candidateDeviceId, String candidateOperation, String candidatePayloadSha256) {
        return deviceId.equals(candidateDeviceId) && operation.equals(candidateOperation)
                && payloadSha256.equals(candidatePayloadSha256);
    }

    private static boolean mayTransition(DeviceCommandStatusChanged.Status from,
            DeviceCommandStatusChanged.Status to) {
        if (from == DeviceCommandStatusChanged.Status.SUCCEEDED
                || from == DeviceCommandStatusChanged.Status.FAILED
                || from == DeviceCommandStatusChanged.Status.EXPIRED) return false;
        return switch (from) {
            case ACCEPTED -> to == DeviceCommandStatusChanged.Status.DISPATCHED
                    || to == DeviceCommandStatusChanged.Status.FAILED
                    || to == DeviceCommandStatusChanged.Status.EXPIRED;
            case DISPATCHED -> to == DeviceCommandStatusChanged.Status.ACKNOWLEDGED
                    || to == DeviceCommandStatusChanged.Status.SUCCEEDED
                    || to == DeviceCommandStatusChanged.Status.FAILED
                    || to == DeviceCommandStatusChanged.Status.EXPIRED;
            case ACKNOWLEDGED -> to == DeviceCommandStatusChanged.Status.SUCCEEDED
                    || to == DeviceCommandStatusChanged.Status.FAILED
                    || to == DeviceCommandStatusChanged.Status.EXPIRED;
            default -> false;
        };
    }

    public UUID commandId() { return commandId; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID deviceId() { return deviceId; }
    public UUID requestedBy() { return requestedBy; }
    public String idempotencyKey() { return idempotencyKey; }
    public String operation() { return operation; }
    public String payloadSha256() { return payloadSha256; }
    public DeviceCommandStatusChanged.Status status() { return status; }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public Instant requestedAt() { return requestedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Optional<Instant> dispatchedAt() { return Optional.ofNullable(dispatchedAt); }
    public Optional<Instant> acknowledgedAt() { return Optional.ofNullable(acknowledgedAt); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public Instant statusUpdatedAt() { return statusUpdatedAt; }
    public long version() { return version; }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 is invalid");
        }
        return value;
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

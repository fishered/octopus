package com.accuenergy.octopus.mgmt.domain.monitoring;

import com.accuenergy.octopus.api.control.DeviceCommandStatusChanged;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Audited desired-state intent; delivery is delegated to the reliable command lifecycle. */
public final class DesiredShadowRequest {
    private final UUID requestId;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID deviceId;
    private final UUID requestedBy;
    private final String idempotencyKey;
    private final long expectedVersion;
    private final long desiredVersion;
    private final String desiredStateJson;
    private final String requestSha256;
    private final UUID commandId;
    private final Instant requestedAt;
    private final Instant expiresAt;
    private Status status;
    private String failureCode;
    private Instant appliedAt;
    private Instant statusUpdatedAt;
    private long projectionVersion;

    private DesiredShadowRequest(UUID requestId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, long expectedVersion, long desiredVersion,
            String desiredStateJson, String requestSha256, UUID commandId, Status status,
            String failureCode, Instant requestedAt, Instant expiresAt, Instant appliedAt,
            Instant statusUpdatedAt, long projectionVersion) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 128);
        if (expectedVersion < 0 || desiredVersion != expectedVersion + 1) {
            throw new IllegalArgumentException("Desired shadow version is invalid");
        }
        this.expectedVersion = expectedVersion;
        this.desiredVersion = desiredVersion;
        this.desiredStateJson = requireState(desiredStateJson);
        if (requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestSha256 is invalid");
        }
        this.requestSha256 = requestSha256;
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.status = Objects.requireNonNull(status, "status");
        this.failureCode = normalize(failureCode);
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("expiresAt must follow requestedAt");
        this.appliedAt = appliedAt;
        this.statusUpdatedAt = Objects.requireNonNull(statusUpdatedAt, "statusUpdatedAt");
        if (projectionVersion < 0) throw new IllegalArgumentException("projectionVersion must be non-negative");
        this.projectionVersion = projectionVersion;
    }

    public static DesiredShadowRequest create(UUID requestId, UUID tenantId, UUID organizationId,
            UUID deviceId, UUID requestedBy, String idempotencyKey, long expectedVersion,
            String desiredStateJson, String requestSha256, UUID commandId, Instant requestedAt,
            Instant expiresAt) {
        return new DesiredShadowRequest(requestId, tenantId, organizationId, deviceId, requestedBy,
                idempotencyKey, expectedVersion, expectedVersion + 1, desiredStateJson, requestSha256,
                commandId, Status.REQUESTED, null, requestedAt, expiresAt, null, requestedAt, 0);
    }

    public static DesiredShadowRequest restore(UUID requestId, UUID tenantId, UUID organizationId,
            UUID deviceId, UUID requestedBy, String idempotencyKey, long expectedVersion, long desiredVersion,
            String desiredStateJson, String requestSha256, UUID commandId, Status status, String failureCode,
            Instant requestedAt, Instant expiresAt, Instant appliedAt, Instant statusUpdatedAt,
            long projectionVersion) {
        return new DesiredShadowRequest(requestId, tenantId, organizationId, deviceId, requestedBy,
                idempotencyKey, expectedVersion, desiredVersion, desiredStateJson, requestSha256, commandId,
                status, failureCode, requestedAt, expiresAt, appliedAt, statusUpdatedAt, projectionVersion);
    }

    public boolean applyCommandStatus(DeviceCommandStatusChanged event) {
        if (!tenantId.equals(event.tenantId()) || !deviceId.equals(event.deviceId())
                || !commandId.equals(event.commandId())) {
            throw new IllegalArgumentException("Command status does not match desired shadow request");
        }
        if (event.occurredAt().isBefore(statusUpdatedAt) || isTerminal()) return false;
        Status target = switch (event.status()) {
            case ACCEPTED -> Status.REQUESTED;
            case DISPATCHED -> Status.DISPATCHED;
            case ACKNOWLEDGED -> Status.ACKNOWLEDGED;
            case SUCCEEDED -> Status.EXECUTED;
            case FAILED -> Status.FAILED;
            case EXPIRED -> Status.EXPIRED;
        };
        if (target == status) return false;
        if (!mayTransition(status, target)) return false;
        status = target;
        failureCode = target == Status.FAILED ? event.failureCode() : null;
        statusUpdatedAt = event.occurredAt();
        projectionVersion++;
        return true;
    }

    public boolean markApplied(long appliedDesiredVersion, Instant at) {
        if (appliedDesiredVersion != desiredVersion || isTerminal()) return false;
        status = Status.APPLIED;
        appliedAt = Objects.requireNonNull(at, "at");
        if (at.isAfter(statusUpdatedAt)) statusUpdatedAt = at;
        failureCode = null;
        projectionVersion++;
        return true;
    }

    public boolean matches(UUID candidateDeviceId, long candidateExpectedVersion, String candidateRequestSha256) {
        return deviceId.equals(candidateDeviceId) && expectedVersion == candidateExpectedVersion
                && requestSha256.equals(candidateRequestSha256);
    }

    public boolean isTerminal() {
        return status == Status.APPLIED || status == Status.FAILED || status == Status.EXPIRED
                || status == Status.SUPERSEDED;
    }

    private static boolean mayTransition(Status from, Status to) {
        if (to == Status.FAILED || to == Status.EXPIRED) return true;
        return switch (from) {
            case REQUESTED -> to == Status.DISPATCHED || to == Status.ACKNOWLEDGED || to == Status.EXECUTED;
            case DISPATCHED -> to == Status.ACKNOWLEDGED || to == Status.EXECUTED;
            case ACKNOWLEDGED -> to == Status.EXECUTED;
            default -> false;
        };
    }

    public UUID requestId() { return requestId; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID deviceId() { return deviceId; }
    public UUID requestedBy() { return requestedBy; }
    public String idempotencyKey() { return idempotencyKey; }
    public long expectedVersion() { return expectedVersion; }
    public long desiredVersion() { return desiredVersion; }
    public String desiredStateJson() { return desiredStateJson; }
    public String requestSha256() { return requestSha256; }
    public UUID commandId() { return commandId; }
    public Status status() { return status; }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public Instant requestedAt() { return requestedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Optional<Instant> appliedAt() { return Optional.ofNullable(appliedAt); }
    public Instant statusUpdatedAt() { return statusUpdatedAt; }
    public long projectionVersion() { return projectionVersion; }

    public enum Status { REQUESTED, DISPATCHED, ACKNOWLEDGED, EXECUTED, APPLIED, FAILED, EXPIRED, SUPERSEDED }

    private static String requireState(String value) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > 61_440) {
            throw new IllegalArgumentException("Desired state is invalid or exceeds 60 KiB");
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

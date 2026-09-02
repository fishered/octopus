package com.accuenergy.octopus.control.domain.command;

import com.accuenergy.octopus.api.control.DeviceCommandRequested;
import com.accuenergy.octopus.api.control.DeviceCommandResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DeviceCommand {
    private final UUID commandId;
    private final UUID tenantId;
    private final UUID deviceId;
    private final UUID requestedBy;
    private final String operation;
    private final byte[] payload;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Status status;
    private Instant dispatchedAt;
    private Instant acknowledgedAt;
    private String failureCode;

    private Instant completedAt;
    private long version;

    private DeviceCommand(UUID commandId, UUID tenantId, UUID deviceId, UUID requestedBy,
                          String operation, byte[] payload, Instant createdAt, Instant expiresAt,
                          Status status, Instant dispatchedAt, Instant acknowledgedAt,
                          Instant completedAt, String failureCode, long version) {
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation is required");
        this.operation = operation.strip();
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("Command expiry must be after creation");
        this.status = Objects.requireNonNull(status, "status");
        this.dispatchedAt = dispatchedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.completedAt = completedAt;
        this.failureCode = failureCode;
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
    }

    public static DeviceCommand accept(UUID commandId, UUID tenantId, UUID deviceId,
                                       Instant createdAt, Instant expiresAt) {
        return new DeviceCommand(commandId, tenantId, deviceId, new UUID(0, 0), "legacy", new byte[0],
                createdAt, expiresAt, Status.ACCEPTED, null, null, null, null, 0);
    }

    public static DeviceCommand accept(DeviceCommandRequested request) {
        return new DeviceCommand(request.commandId(), request.tenantId(), request.deviceId(), request.requestedBy(),
                request.operation(), request.payload(), request.requestedAt(), request.expiresAt(),
                Status.ACCEPTED, null, null, null, null, 0);
    }

    public static DeviceCommand restore(UUID commandId, UUID tenantId, UUID deviceId, UUID requestedBy,
            String operation, byte[] payload, Instant createdAt, Instant expiresAt, Status status,
            Instant dispatchedAt, Instant acknowledgedAt, Instant completedAt, String failureCode, long version) {
        return new DeviceCommand(commandId, tenantId, deviceId, requestedBy, operation, payload, createdAt,
                expiresAt, status, dispatchedAt, acknowledgedAt, completedAt, failureCode, version);
    }

    public void markDispatched(Instant at) {
        require(Status.ACCEPTED);
        requireNotBefore(at, createdAt, "Dispatch precedes command creation");
        if (isAtOrAfterExpiry(at)) {
            expireAt(at);
            return;
        }
        dispatchedAt = at;
        status = Status.DISPATCHED;
        version++;
    }

    public void acknowledge(Instant at) {
        require(Status.DISPATCHED);
        requireNotBefore(at, dispatchedAt, "Acknowledgement precedes dispatch");
        if (isAtOrAfterExpiry(at)) {
            expireAt(at);
            return;
        }
        acknowledgedAt = at;
        status = Status.ACKNOWLEDGED;
        version++;
    }

    public void complete(Instant at) {
        require(Status.ACKNOWLEDGED);
        requireNotBefore(at, acknowledgedAt, "Completion precedes acknowledgement");
        if (isAtOrAfterExpiry(at)) {
            expireAt(at);
            return;
        }
        completedAt = at;
        status = Status.SUCCEEDED;
        version++;
    }

    public void succeed(Instant at) {
        if (status != Status.DISPATCHED && status != Status.ACKNOWLEDGED) {
            throw new IllegalStateException("Command is not ready to complete");
        }
        requireNotBefore(at, status == Status.ACKNOWLEDGED ? acknowledgedAt : dispatchedAt,
                "Completion precedes current command state");
        if (isAtOrAfterExpiry(at)) {
            expireAt(at);
            return;
        }
        if (acknowledgedAt == null) acknowledgedAt = at;
        completedAt = at;
        status = Status.SUCCEEDED;
        version++;
    }

    public void fail(String code, Instant at) {
        if (status == Status.SUCCEEDED || status == Status.FAILED || status == Status.EXPIRED) {
            throw new IllegalStateException("Command is already terminal");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("failure code is required");
        requireNotBefore(at, status == Status.ACKNOWLEDGED ? acknowledgedAt
                : status == Status.DISPATCHED ? dispatchedAt : createdAt,
                "Failure precedes current command state");
        if (isAtOrAfterExpiry(at)) {
            expireAt(at);
            return;
        }
        failureCode = code;
        completedAt = at;
        status = Status.FAILED;
        version++;
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!now.isBefore(expiresAt) && !isTerminal()) expireAt(now);
    }

    public boolean apply(DeviceCommandResult result) {
        if (!tenantId.equals(result.tenantId()) || !deviceId.equals(result.deviceId())
                || !commandId.equals(result.commandId())) {
            throw new IllegalArgumentException("Command result scope mismatch");
        }
        if (isTerminal()) return false;
        return switch (result.status()) {
            case ACKNOWLEDGED -> {
                if (status == Status.ACKNOWLEDGED) yield false;
                if (status != Status.DISPATCHED) throw new IllegalStateException("Command is not dispatched");
                acknowledge(result.occurredAt());
                yield true;
            }
            case SUCCEEDED -> {
                succeed(result.occurredAt());
                yield true;
            }
            case FAILED -> {
                fail(result.failureCode(), result.occurredAt());
                yield true;
            }
        };
    }

    public boolean matches(DeviceCommandRequested request) {
        return commandId.equals(request.commandId()) && tenantId.equals(request.tenantId())
                && deviceId.equals(request.deviceId()) && requestedBy.equals(request.requestedBy())
                && operation.equals(request.operation()) && Arrays.equals(payload, request.payload())
                && createdAt.equals(request.requestedAt()) && expiresAt.equals(request.expiresAt());
    }

    public boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.EXPIRED;
    }

    public UUID commandId() { return commandId; }
    public UUID tenantId() { return tenantId; }
    public UUID deviceId() { return deviceId; }
    public UUID requestedBy() { return requestedBy; }
    public String operation() { return operation; }
    public byte[] payload() { return payload.clone(); }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public Optional<Instant> dispatchedAt() { return Optional.ofNullable(dispatchedAt); }
    public Optional<Instant> acknowledgedAt() { return Optional.ofNullable(acknowledgedAt); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public long version() { return version; }

    private void require(Status expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private boolean isAtOrAfterExpiry(Instant at) {
        return !Objects.requireNonNull(at, "at").isBefore(expiresAt);
    }

    private void expireAt(Instant at) {
        status = Status.EXPIRED;
        completedAt = at;
        version++;
    }

    private static void requireNotBefore(Instant candidate, Instant reference, String message) {
        if (Objects.requireNonNull(candidate, "candidate").isBefore(Objects.requireNonNull(reference, "reference"))) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum Status { ACCEPTED, DISPATCHED, ACKNOWLEDGED, SUCCEEDED, FAILED, EXPIRED }
}

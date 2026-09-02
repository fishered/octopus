package com.accuenergy.octopus.mgmt.domain.alarm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AlarmIncident {
    private final UUID id;
    private final UUID tenantId;
    private final UUID ruleId;
    private final UUID organizationId;
    private final UUID deviceId;
    private final UUID meterId;
    private final UUID parameterId;
    private final AlarmRule.Severity severity;
    private final Instant openedAt;
    private final BigDecimal triggerValue;
    private State state;
    private BigDecimal latestValue;
    private long occurrenceCount;
    private Instant lastObservedAt;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant clearedAt;
    private long version;
    private Instant updatedAt;

    private AlarmIncident(UUID id, UUID tenantId, UUID ruleId, UUID organizationId, UUID deviceId,
            UUID meterId, UUID parameterId, AlarmRule.Severity severity, State state,
            BigDecimal triggerValue, BigDecimal latestValue, long occurrenceCount, Instant openedAt,
            Instant lastObservedAt, UUID acknowledgedBy, Instant acknowledgedAt, Instant clearedAt,
            long version, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.meterId = Objects.requireNonNull(meterId, "meterId");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.state = Objects.requireNonNull(state, "state");
        this.triggerValue = Objects.requireNonNull(triggerValue, "triggerValue");
        this.latestValue = Objects.requireNonNull(latestValue, "latestValue");
        if (occurrenceCount < 1) throw new IllegalArgumentException("occurrenceCount must be positive");
        this.occurrenceCount = occurrenceCount;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.lastObservedAt = Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.clearedAt = clearedAt;
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static AlarmIncident open(UUID id, AlarmRule rule, BigDecimal value, Instant occurredAt, Instant createdAt) {
        return new AlarmIncident(id, rule.tenantId(), rule.id(), rule.organizationId(), rule.deviceId(),
                rule.meterId(), rule.parameterId(), rule.severity(), State.OPEN, value, value, 1,
                occurredAt, occurredAt, null, null, null, 0, createdAt);
    }

    public static AlarmIncident restore(UUID id, UUID tenantId, UUID ruleId, UUID organizationId,
            UUID deviceId, UUID meterId, UUID parameterId, AlarmRule.Severity severity, State state,
            BigDecimal triggerValue, BigDecimal latestValue, long occurrenceCount, Instant openedAt,
            Instant lastObservedAt, UUID acknowledgedBy, Instant acknowledgedAt, Instant clearedAt,
            long version, Instant updatedAt) {
        return new AlarmIncident(id, tenantId, ruleId, organizationId, deviceId, meterId, parameterId,
                severity, state, triggerValue, latestValue, occurrenceCount, openedAt, lastObservedAt,
                acknowledgedBy, acknowledgedAt, clearedAt, version, updatedAt);
    }

    /** Late telemetry is retained elsewhere but cannot roll current alarm state backward. */
    public boolean observe(BigDecimal value, Instant occurredAt, Instant processedAt) {
        requireOpen();
        if (occurredAt.isBefore(lastObservedAt)) return false;
        latestValue = Objects.requireNonNull(value, "value");
        lastObservedAt = Objects.requireNonNull(occurredAt, "occurredAt");
        occurrenceCount++;
        touch(processedAt);
        return true;
    }

    public boolean clear(BigDecimal value, Instant occurredAt, Instant processedAt) {
        requireOpen();
        if (occurredAt.isBefore(lastObservedAt)) return false;
        latestValue = Objects.requireNonNull(value, "value");
        lastObservedAt = Objects.requireNonNull(occurredAt, "occurredAt");
        clearedAt = occurredAt;
        state = State.CLEARED;
        touch(processedAt);
        return true;
    }

    public boolean acknowledge(UUID accountId, Instant at) {
        if (state == State.CLEARED) throw new IllegalStateException("Cleared alarm cannot be acknowledged");
        if (state == State.ACKNOWLEDGED) return false;
        acknowledgedBy = Objects.requireNonNull(accountId, "accountId");
        acknowledgedAt = Objects.requireNonNull(at, "at");
        state = State.ACKNOWLEDGED;
        touch(at);
        return true;
    }

    private void requireOpen() {
        if (state == State.CLEARED) throw new IllegalStateException("Alarm incident is already cleared");
    }

    private void touch(Instant at) {
        updatedAt = at;
        version++;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID ruleId() { return ruleId; }
    public UUID organizationId() { return organizationId; }
    public UUID deviceId() { return deviceId; }
    public UUID meterId() { return meterId; }
    public UUID parameterId() { return parameterId; }
    public AlarmRule.Severity severity() { return severity; }
    public State state() { return state; }
    public BigDecimal triggerValue() { return triggerValue; }
    public BigDecimal latestValue() { return latestValue; }
    public long occurrenceCount() { return occurrenceCount; }
    public Instant openedAt() { return openedAt; }
    public Instant lastObservedAt() { return lastObservedAt; }
    public Optional<UUID> acknowledgedBy() { return Optional.ofNullable(acknowledgedBy); }
    public Optional<Instant> acknowledgedAt() { return Optional.ofNullable(acknowledgedAt); }
    public Optional<Instant> clearedAt() { return Optional.ofNullable(clearedAt); }
    public long version() { return version; }
    public Instant updatedAt() { return updatedAt; }

    public enum State { OPEN, ACKNOWLEDGED, CLEARED }
}

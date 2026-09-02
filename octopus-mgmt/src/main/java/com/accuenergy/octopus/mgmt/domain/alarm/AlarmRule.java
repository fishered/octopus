package com.accuenergy.octopus.mgmt.domain.alarm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AlarmRule {
    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID deviceId;
    private final UUID meterId;
    private final UUID parameterId;
    private final String code;
    private final String displayName;
    private final ValueSelector valueSelector;
    private final Comparison comparison;
    private final BigDecimal triggerThreshold;
    private final BigDecimal clearThreshold;
    private final Severity severity;
    private final Status status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AlarmRule(UUID id, UUID tenantId, UUID organizationId, UUID deviceId, UUID meterId,
            UUID parameterId, String code, String displayName, ValueSelector valueSelector,
            Comparison comparison, BigDecimal triggerThreshold, BigDecimal clearThreshold,
            Severity severity, Status status, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.meterId = Objects.requireNonNull(meterId, "meterId");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
        this.code = requireText(code, "code", 64);
        this.displayName = requireText(displayName, "displayName", 128);
        this.valueSelector = Objects.requireNonNull(valueSelector, "valueSelector");
        this.comparison = Objects.requireNonNull(comparison, "comparison");
        this.triggerThreshold = Objects.requireNonNull(triggerThreshold, "triggerThreshold");
        this.clearThreshold = clearThreshold == null ? triggerThreshold : clearThreshold;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        validateHysteresis();
    }

    public static AlarmRule create(UUID id, UUID tenantId, UUID organizationId, UUID deviceId, UUID meterId,
            UUID parameterId, String code, String displayName, ValueSelector valueSelector,
            Comparison comparison, BigDecimal triggerThreshold, BigDecimal clearThreshold,
            Severity severity, Instant now) {
        return new AlarmRule(id, tenantId, organizationId, deviceId, meterId, parameterId, code, displayName,
                valueSelector, comparison, triggerThreshold, clearThreshold, severity, Status.ACTIVE, 0, now, now);
    }

    public static AlarmRule restore(UUID id, UUID tenantId, UUID organizationId, UUID deviceId, UUID meterId,
            UUID parameterId, String code, String displayName, ValueSelector valueSelector,
            Comparison comparison, BigDecimal triggerThreshold, BigDecimal clearThreshold,
            Severity severity, Status status, long version, Instant createdAt, Instant updatedAt) {
        return new AlarmRule(id, tenantId, organizationId, deviceId, meterId, parameterId, code, displayName,
                valueSelector, comparison, triggerThreshold, clearThreshold, severity, status, version,
                createdAt, updatedAt);
    }

    public boolean isTriggered(BigDecimal value) {
        if (status != Status.ACTIVE) return false;
        int result = Objects.requireNonNull(value, "value").compareTo(triggerThreshold);
        return comparison.test(result);
    }

    public boolean shouldClear(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return switch (comparison) {
            case GREATER_THAN, GREATER_OR_EQUAL -> value.compareTo(clearThreshold) <= 0;
            case LESS_THAN, LESS_OR_EQUAL -> value.compareTo(clearThreshold) >= 0;
            case EQUAL -> value.compareTo(triggerThreshold) != 0;
            case NOT_EQUAL -> value.compareTo(triggerThreshold) == 0;
        };
    }

    public BigDecimal selectedValue(BigDecimal raw, BigDecimal delta, BigDecimal intervalAccumulation) {
        return switch (valueSelector) {
            case RAW -> Objects.requireNonNull(raw, "raw");
            case DELTA -> Objects.requireNonNull(delta, "delta");
            case INTERVAL_ACCUMULATION -> Objects.requireNonNull(intervalAccumulation, "intervalAccumulation");
        };
    }

    public boolean matchesScope(UUID telemetryTenantId, UUID telemetryDeviceId, UUID telemetryMeterId,
            UUID telemetryParameterId) {
        return tenantId.equals(telemetryTenantId) && deviceId.equals(telemetryDeviceId)
                && meterId.equals(telemetryMeterId) && parameterId.equals(telemetryParameterId);
    }

    private void validateHysteresis() {
        int relation = clearThreshold.compareTo(triggerThreshold);
        if ((comparison == Comparison.GREATER_THAN || comparison == Comparison.GREATER_OR_EQUAL) && relation > 0) {
            throw new IllegalArgumentException("Greater-than alarms require clearThreshold <= triggerThreshold");
        }
        if ((comparison == Comparison.LESS_THAN || comparison == Comparison.LESS_OR_EQUAL) && relation < 0) {
            throw new IllegalArgumentException("Less-than alarms require clearThreshold >= triggerThreshold");
        }
        if ((comparison == Comparison.EQUAL || comparison == Comparison.NOT_EQUAL) && relation != 0) {
            throw new IllegalArgumentException("Equality alarms do not support a distinct clearThreshold");
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID deviceId() { return deviceId; }
    public UUID meterId() { return meterId; }
    public UUID parameterId() { return parameterId; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public ValueSelector valueSelector() { return valueSelector; }
    public Comparison comparison() { return comparison; }
    public BigDecimal triggerThreshold() { return triggerThreshold; }
    public BigDecimal clearThreshold() { return clearThreshold; }
    public Severity severity() { return severity; }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum ValueSelector { RAW, DELTA, INTERVAL_ACCUMULATION }
    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Status { ACTIVE, DISABLED }
    public enum Comparison {
        GREATER_THAN { boolean test(int value) { return value > 0; } },
        GREATER_OR_EQUAL { boolean test(int value) { return value >= 0; } },
        LESS_THAN { boolean test(int value) { return value < 0; } },
        LESS_OR_EQUAL { boolean test(int value) { return value <= 0; } },
        EQUAL { boolean test(int value) { return value == 0; } },
        NOT_EQUAL { boolean test(int value) { return value != 0; } };

        abstract boolean test(int value);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

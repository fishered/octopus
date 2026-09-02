package com.accuenergy.octopus.mgmt.domain.meter;

import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Addressable telemetry point belonging to one device. */
public final class Meter {
    private final UUID id;
    private final UUID tenantId;
    private final UUID deviceId;
    private final UUID facilityId;
    private final UUID parameterId;
    private final UUID unitId;
    private final String code;
    private final String displayName;
    private final Kind kind;
    private final String calculationExpression;
    private final BigDecimal rolloverModulus;
    private final int decimalScale;
    private final ValueSemantics semantics;
    private Status status;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Meter(UUID id, UUID tenantId, UUID deviceId, UUID facilityId, UUID parameterId,
                  UUID unitId, String code, String displayName, Kind kind,
                  String calculationExpression, BigDecimal rolloverModulus, int decimalScale,
                  Status status, long version, Instant createdAt, Instant updatedAt,
                  ValueSemantics semantics) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.facilityId = facilityId;
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.code = requireText(code, "code", 128);
        this.displayName = requireText(displayName, "displayName", 200);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.calculationExpression = normalize(calculationExpression);
        this.rolloverModulus = rolloverModulus == null ? null : rolloverModulus.stripTrailingZeros();
        if (decimalScale < 0 || decimalScale > 18) {
            throw new IllegalArgumentException("decimalScale must be between 0 and 18");
        }
        this.decimalScale = decimalScale;
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        validateConfiguration(this.semantics);
    }

    public static Meter create(UUID id, UUID tenantId, UUID deviceId, UUID facilityId,
                               UUID parameterId, UUID unitId, String code, String displayName,
                               Kind kind, String calculationExpression, BigDecimal rolloverModulus,
                               int decimalScale, ValueSemantics semantics, Instant now) {
        return new Meter(id, tenantId, deviceId, facilityId, parameterId, unitId, code, displayName,
                kind, calculationExpression, rolloverModulus, decimalScale, Status.ACTIVE, 0,
                now, now, semantics);
    }

    public static Meter restore(UUID id, UUID tenantId, UUID deviceId, UUID facilityId,
                                UUID parameterId, UUID unitId, String code, String displayName,
                                Kind kind, String calculationExpression, BigDecimal rolloverModulus,
                                int decimalScale, Status status, long version, Instant createdAt,
                                Instant updatedAt, ValueSemantics semantics) {
        return new Meter(id, tenantId, deviceId, facilityId, parameterId, unitId, code, displayName,
                kind, calculationExpression, rolloverModulus, decimalScale, status, version,
                createdAt, updatedAt, semantics);
    }

    private void validateConfiguration(ValueSemantics semantics) {
        if (kind == Kind.CALCULATED && calculationExpression == null) {
            throw new IllegalArgumentException("Calculated meters require an expression");
        }
        if (kind != Kind.CALCULATED && calculationExpression != null) {
            throw new IllegalArgumentException("Only calculated meters can define an expression");
        }
        if (rolloverModulus != null) {
            if (semantics != ValueSemantics.CUMULATIVE) {
                throw new IllegalArgumentException("Rollover is only valid for cumulative parameters");
            }
            if (rolloverModulus.signum() <= 0) {
                throw new IllegalArgumentException("rolloverModulus must be positive");
            }
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID deviceId() { return deviceId; }
    public Optional<UUID> facilityId() { return Optional.ofNullable(facilityId); }
    public UUID parameterId() { return parameterId; }
    public UUID unitId() { return unitId; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public Kind kind() { return kind; }
    public Optional<String> calculationExpression() { return Optional.ofNullable(calculationExpression); }
    public Optional<BigDecimal> rolloverModulus() { return Optional.ofNullable(rolloverModulus); }
    public int decimalScale() { return decimalScale; }
    public ValueSemantics semantics() { return semantics; }
    public Status status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public enum Kind { STANDARD, CALCULATED, BILLING, GATEWAY }
    public enum Status { ACTIVE, SUSPENDED, RETIRED }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

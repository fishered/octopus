package com.accuenergy.octopus.mgmt.domain.catalog;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Semantic definition shared by all meters that measure the same physical quantity. */
public record ParameterDefinition(
        UUID id,
        Optional<UUID> tenantId,
        String code,
        String displayName,
        String quantityKind,
        ValueSemantics valueSemantics,
        DataType dataType,
        Optional<UUID> canonicalUnitId,
        Optional<Integer> decimalScale,
        Status status) {

    public ParameterDefinition {
        Objects.requireNonNull(id, "id");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        code = requireText(code, "code", 64);
        displayName = requireText(displayName, "displayName", 200);
        quantityKind = requireText(quantityKind, "quantityKind", 64);
        Objects.requireNonNull(valueSemantics, "valueSemantics");
        Objects.requireNonNull(dataType, "dataType");
        canonicalUnitId = Objects.requireNonNull(canonicalUnitId, "canonicalUnitId");
        decimalScale = Objects.requireNonNull(decimalScale, "decimalScale");
        Objects.requireNonNull(status, "status");

        if (dataType.isNumeric() && canonicalUnitId.isEmpty()) {
            throw new IllegalArgumentException("Numeric parameters require a canonical unit");
        }
        if (!dataType.isNumeric() && (canonicalUnitId.isPresent() || decimalScale.isPresent())) {
            throw new IllegalArgumentException("Non-numeric parameters cannot define unit or decimal scale");
        }
        decimalScale.ifPresent(scale -> {
            if (scale < 0 || scale > 18) throw new IllegalArgumentException("decimalScale must be between 0 and 18");
        });
    }

    public enum ValueSemantics { INSTANTANEOUS, CUMULATIVE, INTERVAL_DELTA, NET }

    public enum DataType {
        DECIMAL, INTEGER, BOOLEAN, STRING;
        public boolean isNumeric() { return this == DECIMAL || this == INTEGER; }
    }

    public enum Status { ACTIVE, RETIRED }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

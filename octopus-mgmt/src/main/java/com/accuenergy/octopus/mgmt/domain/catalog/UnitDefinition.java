package com.accuenergy.octopus.mgmt.domain.catalog;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A globally shared, UCUM-compatible conversion into a quantity's canonical unit. */
public record UnitDefinition(
        UUID id,
        String code,
        String symbol,
        String dimension,
        BigDecimal scale,
        BigDecimal offset,
        Optional<String> description) {

    public UnitDefinition {
        Objects.requireNonNull(id, "id");
        code = requireText(code, "code", 64);
        symbol = requireText(symbol, "symbol", 32);
        dimension = requireText(dimension, "dimension", 64);
        scale = Objects.requireNonNull(scale, "scale").stripTrailingZeros();
        offset = Objects.requireNonNull(offset, "offset").stripTrailingZeros();
        description = Objects.requireNonNull(description, "description")
                .map(value -> requireText(value, "description", 500));
        if (scale.signum() <= 0) throw new IllegalArgumentException("scale must be positive");
    }

    public BigDecimal toCanonical(BigDecimal value) {
        return Objects.requireNonNull(value, "value").multiply(scale).add(offset);
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

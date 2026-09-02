package com.accuenergy.octopus.common.core;

import java.util.Objects;
import java.util.UUID;

/** A globally unique, opaque identifier safe for cross-region event contracts. */
public record GlobalId(UUID value) {
    public GlobalId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static GlobalId newId() {
        return new GlobalId(UUID.randomUUID());
    }

    public static GlobalId parse(String value) {
        return new GlobalId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}


package com.accuenergy.octopus.mgmt.domain.identity;

import java.util.Objects;

/** Global action vocabulary entry that is safe to delegate through tenant roles. */
public record PermissionDefinition(String code, String resourceType, String action, String description) {
    public PermissionDefinition {
        code = requireText(code, "code", 128);
        resourceType = requireText(resourceType, "resourceType", 64);
        action = requireText(action, "action", 64);
        description = requireText(description, "description", 500);
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }
}

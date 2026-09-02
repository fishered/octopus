package com.accuenergy.octopus.common.security;

import java.util.Objects;
import java.util.UUID;

public record ResourceGrant(String resourceType, UUID resourceId, ResourceAction action) {
    public ResourceGrant {
        if (resourceType == null || resourceType.isBlank()) throw new IllegalArgumentException("resourceType is required");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(action, "action");
    }
}


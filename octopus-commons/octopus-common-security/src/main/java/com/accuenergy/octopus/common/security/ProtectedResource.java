package com.accuenergy.octopus.common.security;

import com.accuenergy.octopus.common.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProtectedResource(TenantId tenantId, String resourceType, UUID resourceId,
                                Optional<String> organizationPath) {
    public ProtectedResource {
        Objects.requireNonNull(tenantId, "tenantId");
        if (resourceType == null || resourceType.isBlank()) throw new IllegalArgumentException("resourceType is required");
        Objects.requireNonNull(resourceId, "resourceId");
        organizationPath = Objects.requireNonNull(organizationPath, "organizationPath");
    }
}


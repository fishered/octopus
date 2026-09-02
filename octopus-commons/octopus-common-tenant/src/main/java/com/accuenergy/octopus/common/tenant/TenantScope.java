package com.accuenergy.octopus.common.tenant;

import java.util.Objects;
import java.util.Optional;

public sealed interface TenantScope permits TenantScope.Scoped, TenantScope.Platform {
    Optional<TenantId> tenantId();

    record Scoped(TenantId value) implements TenantScope {
        public Scoped {
            Objects.requireNonNull(value, "tenantId must not be null");
        }

        @Override
        public Optional<TenantId> tenantId() {
            return Optional.of(value);
        }
    }

    record Platform(String auditedReason) implements TenantScope {
        public Platform {
            if (auditedReason == null || auditedReason.isBlank()) {
                throw new IllegalArgumentException("Platform scope requires an audited reason");
            }
        }

        @Override
        public Optional<TenantId> tenantId() {
            return Optional.empty();
        }
    }
}


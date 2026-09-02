package com.accuenergy.octopus.mgmt.domain.identity;

import com.accuenergy.octopus.common.security.ResourceAction;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Audited, tenant-owned explicit resource access attached to one account membership. */
public record ManagedResourceGrant(
        UUID id,
        UUID tenantId,
        UUID membershipId,
        ResourceType resourceType,
        UUID resourceId,
        ResourceAction action,
        Optional<UUID> createdByAccountId,
        Instant createdAt,
        Optional<UUID> revokedByAccountId,
        Optional<Instant> revokedAt,
        Optional<String> revokeReason) {

    public ManagedResourceGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(action, "action");
        createdByAccountId = Objects.requireNonNull(createdByAccountId, "createdByAccountId");
        Objects.requireNonNull(createdAt, "createdAt");
        revokedByAccountId = Objects.requireNonNull(revokedByAccountId, "revokedByAccountId");
        revokedAt = Objects.requireNonNull(revokedAt, "revokedAt");
        revokeReason = Objects.requireNonNull(revokeReason, "revokeReason");
        if (!resourceType.allowedActions().contains(action)) {
            throw new IllegalArgumentException("Action is not valid for resource type");
        }
        boolean revoked = revokedAt.isPresent();
        if (revoked != revokedByAccountId.isPresent() || revoked != revokeReason.isPresent()) {
            throw new IllegalArgumentException("Revocation metadata must be complete");
        }
        revokeReason = revokeReason.map(reason -> requireReason(reason));
    }

    public static ManagedResourceGrant active(UUID id, UUID tenantId, UUID membershipId,
                                              ResourceType resourceType, UUID resourceId,
                                              ResourceAction action, UUID actorAccountId, Instant now) {
        return new ManagedResourceGrant(id, tenantId, membershipId, resourceType, resourceId, action,
                Optional.of(actorAccountId), now, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean active() { return revokedAt.isEmpty(); }

    public String permissionCode() {
        return resourceType.code() + ":" + action.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireReason(String reason) {
        if (reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("revokeReason is invalid");
        }
        return reason.strip();
    }

    public enum ResourceType {
        DEVICE("device", EnumSet.of(ResourceAction.VIEW, ResourceAction.OPERATE, ResourceAction.CONFIGURE)),
        FACILITY("facility", EnumSet.of(ResourceAction.VIEW, ResourceAction.CONFIGURE)),
        METER("meter", EnumSet.of(ResourceAction.VIEW, ResourceAction.CONFIGURE));

        private final String code;
        private final Set<ResourceAction> allowedActions;

        ResourceType(String code, Set<ResourceAction> allowedActions) {
            this.code = code;
            this.allowedActions = Set.copyOf(allowedActions);
        }

        public String code() { return code; }
        public Set<ResourceAction> allowedActions() { return allowedActions; }

        public static ResourceType fromCode(String code) {
            for (ResourceType value : values()) {
                if (value.code.equals(code)) return value;
            }
            throw new IllegalArgumentException("Unsupported resource type: " + code);
        }
    }
}

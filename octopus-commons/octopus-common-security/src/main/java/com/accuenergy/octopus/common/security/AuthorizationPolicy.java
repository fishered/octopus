package com.accuenergy.octopus.common.security;

import java.util.Objects;

/** Deny-by-default RBAC plus organization/data-scope policy. */
public final class AuthorizationPolicy {
    public boolean isAllowed(AuthenticatedPrincipal principal, String permission,
                             ResourceAction action, ProtectedResource resource) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resource, "resource");

        if (principal.type() == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) return true;
        if (!principal.tenantId().orElseThrow().equals(resource.tenantId())) return false;

        boolean scoped = resource.organizationPath()
                .map(path -> principal.organizationScopes().stream()
                        .anyMatch(scope -> scope.allows(permission, path)))
                .orElse(false);
        if (scoped) return true;
        return principal.permissions().contains(permission)
                && principal.resourceGrants().contains(
                        new ResourceGrant(resource.resourceType(), resource.resourceId(), action));
    }
}

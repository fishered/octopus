package com.accuenergy.octopus.mgmt.application.identity;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.OrganizationAccessScope;
import com.accuenergy.octopus.common.security.ResourceGrant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AuthorizationSnapshot(
        AuthenticatedPrincipal.PrincipalType principalType,
        Optional<UUID> tenantId,
        long generation,
        Set<String> permissions,
        Set<OrganizationAccessScope> organizationScopes,
        Set<ResourceGrant> resourceGrants) {
    public AuthorizationSnapshot {
        Objects.requireNonNull(principalType, "principalType");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        permissions = Set.copyOf(permissions);
        organizationScopes = Set.copyOf(organizationScopes);
        resourceGrants = Set.copyOf(resourceGrants);
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        if (principalType == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN && tenantId.isPresent()) {
            throw new IllegalArgumentException("Platform authorization cannot have a tenant");
        }
        if (principalType != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN && tenantId.isEmpty()) {
            throw new IllegalArgumentException("Tenant authorization requires a tenant");
        }
        if (principalType == AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN
                && !organizationScopes.isEmpty()) {
            throw new IllegalArgumentException("Platform authorization cannot have organization scopes");
        }
        boolean hasAdministratorScope = organizationScopes.stream().anyMatch(scope ->
                scope.accessLevel() == OrganizationAccessScope.AccessLevel.ADMINISTRATOR);
        if (principalType == AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN
                && !hasAdministratorScope) {
            throw new IllegalArgumentException("Organization administrator requires an administrator scope");
        }
        if (principalType == AuthenticatedPrincipal.PrincipalType.OPERATOR && hasAdministratorScope) {
            throw new IllegalArgumentException("Operator cannot carry an administrator scope");
        }
    }
}

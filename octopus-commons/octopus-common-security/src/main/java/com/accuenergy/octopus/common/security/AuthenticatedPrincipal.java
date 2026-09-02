package com.accuenergy.octopus.common.security;

import com.accuenergy.octopus.common.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Security identity created only after token signature and authoritative session validation. */
public record AuthenticatedPrincipal(
        UUID accountId,
        UUID sessionId,
        long sessionGeneration,
        PrincipalType type,
        Optional<TenantId> tenantId,
        Set<String> permissions,
        Set<String> organizationPaths,
        Set<OrganizationAccessScope> organizationScopes,
        Set<ResourceGrant> resourceGrants) {

    public AuthenticatedPrincipal {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(type, "type");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        permissions = Set.copyOf(permissions);
        organizationScopes = Set.copyOf(organizationScopes);
        organizationPaths = organizationScopes.stream()
                .map(OrganizationAccessScope::organizationPath)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        resourceGrants = Set.copyOf(resourceGrants);
        if (sessionGeneration < 0) throw new IllegalArgumentException("sessionGeneration must be non-negative");
        if (type == PrincipalType.PLATFORM_ADMIN && tenantId.isPresent()) {
            throw new IllegalArgumentException("Platform administrators must use platform scope");
        }
        if (type != PrincipalType.PLATFORM_ADMIN && tenantId.isEmpty()) {
            throw new IllegalArgumentException("Non-platform principals require a tenant");
        }
        if (type == PrincipalType.PLATFORM_ADMIN && !organizationScopes.isEmpty()) {
            throw new IllegalArgumentException("Platform administrators cannot have organization scopes");
        }
        boolean hasAdministratorScope = organizationScopes.stream().anyMatch(scope ->
                scope.accessLevel() == OrganizationAccessScope.AccessLevel.ADMINISTRATOR);
        if (type == PrincipalType.ORGANIZATION_ADMIN && !hasAdministratorScope) {
            throw new IllegalArgumentException("Organization administrator requires an administrator scope");
        }
        if (type == PrincipalType.OPERATOR && hasAdministratorScope) {
            throw new IllegalArgumentException("Operator cannot carry an administrator scope");
        }
    }

    /** Compatibility constructor for tests and trusted in-process adapters; JWTs use explicit scopes. */
    public AuthenticatedPrincipal(UUID accountId, UUID sessionId, long sessionGeneration,
                                  PrincipalType type, Optional<TenantId> tenantId,
                                  Set<String> permissions, Set<String> organizationPaths,
                                  Set<ResourceGrant> resourceGrants) {
        this(accountId, sessionId, sessionGeneration, type, tenantId, permissions, organizationPaths,
                legacyScopes(type, permissions, organizationPaths), resourceGrants);
    }

    private static Set<OrganizationAccessScope> legacyScopes(PrincipalType type, Set<String> permissions,
                                                              Set<String> paths) {
        if (type == PrincipalType.PLATFORM_ADMIN) return Set.of();
        OrganizationAccessScope.AccessLevel level = type == PrincipalType.ORGANIZATION_ADMIN
                ? OrganizationAccessScope.AccessLevel.ADMINISTRATOR
                : OrganizationAccessScope.AccessLevel.OPERATOR;
        Set<String> scopedPermissions = level == OrganizationAccessScope.AccessLevel.ADMINISTRATOR
                ? Set.of() : permissions;
        return paths.stream().map(path -> new OrganizationAccessScope(path, level, scopedPermissions))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public enum PrincipalType { PLATFORM_ADMIN, ORGANIZATION_ADMIN, OPERATOR }
}

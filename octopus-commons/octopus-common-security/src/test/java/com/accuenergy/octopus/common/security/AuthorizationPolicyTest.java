package com.accuenergy.octopus.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.tenant.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationPolicyTest {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID deviceId = UUID.randomUUID();

    @Test
    void organizationAdminIsLimitedToOwnSubtree() {
        var principal = principal(AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN,
                Set.of(), Set.of("/root/east"), Set.of());
        assertTrue(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/east/site-a")));
        assertFalse(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/west")));
    }

    @Test
    void operatorRequiresPermissionAndDataScopeOrExplicitGrant() {
        var principal = principal(AuthenticatedPrincipal.PrincipalType.OPERATOR,
                Set.of("device:view"), Set.of("/root/east"),
                Set.of(new ResourceGrant("device", deviceId, ResourceAction.VIEW)));
        assertTrue(policy.isAllowed(principal, "device:view", ResourceAction.VIEW,
                resource(tenant, "/root/west")));
        assertFalse(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/east")));
        assertFalse(policy.isAllowed(principal, "device:view", ResourceAction.VIEW,
                resource(new TenantId(UUID.randomUUID()), "/root/east")));
    }

    @Test
    void permissionDoesNotBleedAcrossMembershipOrganizationScopes() {
        var principal = scopedPrincipal(AuthenticatedPrincipal.PrincipalType.OPERATOR,
                Set.of("device:view", "device:operate"), Set.of(
                        new OrganizationAccessScope("/root/east",
                                OrganizationAccessScope.AccessLevel.OPERATOR, Set.of("device:view")),
                        new OrganizationAccessScope("/root/west",
                                OrganizationAccessScope.AccessLevel.OPERATOR, Set.of("device:operate"))));

        assertTrue(policy.isAllowed(principal, "device:view", ResourceAction.VIEW,
                resource(tenant, "/root/east/site")));
        assertFalse(policy.isAllowed(principal, "device:view", ResourceAction.VIEW,
                resource(tenant, "/root/west/site")));
        assertTrue(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/west/site")));
    }

    @Test
    void administratorScopeDoesNotElevateOtherMembershipScopes() {
        var principal = scopedPrincipal(AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN,
                Set.of("device:view", "device:operate"), Set.of(
                        new OrganizationAccessScope("/root/east",
                                OrganizationAccessScope.AccessLevel.ADMINISTRATOR, Set.of()),
                        new OrganizationAccessScope("/root/west",
                                OrganizationAccessScope.AccessLevel.OPERATOR, Set.of("device:view"))));

        assertTrue(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/east/site")));
        assertTrue(policy.isAllowed(principal, "device:view", ResourceAction.VIEW,
                resource(tenant, "/root/west/site")));
        assertFalse(policy.isAllowed(principal, "device:operate", ResourceAction.OPERATE,
                resource(tenant, "/root/west/site")));
    }

    private AuthenticatedPrincipal principal(AuthenticatedPrincipal.PrincipalType type, Set<String> permissions,
                                             Set<String> paths, Set<ResourceGrant> grants) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 1, type,
                Optional.of(tenant), permissions, paths, grants);
    }

    private AuthenticatedPrincipal scopedPrincipal(AuthenticatedPrincipal.PrincipalType type,
            Set<String> permissions, Set<OrganizationAccessScope> scopes) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 1, type,
                Optional.of(tenant), permissions, Set.of(), scopes, Set.of());
    }

    private ProtectedResource resource(TenantId tenantId, String path) {
        return new ProtectedResource(tenantId, "device", deviceId, Optional.of(path));
    }
}

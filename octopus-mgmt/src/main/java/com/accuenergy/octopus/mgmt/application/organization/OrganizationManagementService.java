package com.accuenergy.octopus.mgmt.application.organization;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.organization.Organization;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public final class OrganizationManagementService {
    private final OrganizationRepository organizations;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public OrganizationManagementService(OrganizationRepository organizations,
                                         AuthorizationPolicy authorization, Clock clock) {
        this.organizations = organizations;
        this.authorization = authorization;
        this.clock = clock;
    }

    public Organization create(AuthenticatedPrincipal principal, CreateOrganization command) {
        TenantId tenantId = currentTenant();
        if (organizations.existsByCode(command.code())) {
            throw new IllegalArgumentException("Organization code already exists");
        }
        UUID id = UUID.randomUUID();
        Organization organization;
        if (command.parentId() == null) {
            if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
                throw new OrganizationAccessDeniedException();
            }
            organization = Organization.createRoot(id, tenantId.value(), command.code(),
                    command.displayName(), command.zoneId(), clock.instant());
        } else {
            Organization parent = organizations.find(command.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parent organization"));
            if (!authorization.isAllowed(principal, "organization:create", ResourceAction.CONFIGURE,
                    new ProtectedResource(tenantId, "organization", parent.id(), Optional.of(parent.path())))) {
                throw new OrganizationAccessDeniedException();
            }
            organization = Organization.createChild(id, tenantId.value(), parent.id(), parent.path(),
                    command.code(), command.displayName(), command.zoneId(), clock.instant());
        }
        organizations.insert(organization);
        return organization;
    }

    public Organization get(AuthenticatedPrincipal principal, UUID organizationId) {
        Organization organization = organizations.find(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        if (!authorization.isAllowed(principal, "organization:view", ResourceAction.VIEW,
                new ProtectedResource(new TenantId(organization.tenantId()), "organization", organization.id(),
                        Optional.of(organization.path())))) {
            throw new OrganizationAccessDeniedException();
        }
        return organization;
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record CreateOrganization(UUID parentId, String code, String displayName, String zoneId) { }
}

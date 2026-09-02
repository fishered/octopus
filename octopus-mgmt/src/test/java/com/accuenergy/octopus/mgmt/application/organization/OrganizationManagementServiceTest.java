package com.accuenergy.octopus.mgmt.application.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.organization.Organization;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationManagementServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final MemoryRepository repository = new MemoryRepository();
    private final OrganizationManagementService service = new OrganizationManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void platformCreatesRootAndOrganizationAdminCreatesChild() throws Exception {
        Organization root = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.create(platform(), new OrganizationManagementService.CreateOrganization(
                        null, "root", "Root", "Europe/London")));
        Organization child = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.create(orgAdmin(root.path()), new OrganizationManagementService.CreateOrganization(
                        root.id(), "east", "East", "Asia/Shanghai")));
        assertEquals(root.id(), child.parentId().orElseThrow());
        assertTrue(child.path().startsWith(root.path() + "/"));
    }

    @Test
    void tenantPrincipalCannotCreateAnotherRoot() {
        assertThrows(OrganizationAccessDeniedException.class, () -> TenantContext.run(
                new TenantScope.Scoped(tenant), () -> service.create(orgAdmin("/"),
                        new OrganizationManagementService.CreateOrganization(null, "root", "Root", null))));
    }

    private AuthenticatedPrincipal platform() {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN, Optional.empty(),
                Set.of("platform:all"), Set.of(), Set.of());
    }

    private AuthenticatedPrincipal orgAdmin(String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.ORGANIZATION_ADMIN, Optional.of(tenant),
                Set.of(), Set.of(path), Set.of());
    }

    private static final class MemoryRepository implements OrganizationRepository {
        private final Map<UUID, Organization> values = new HashMap<>();
        public boolean existsByCode(String code) { return values.values().stream().anyMatch(v -> v.code().equals(code)); }
        public void insert(Organization organization) { values.put(organization.id(), organization); }
        public Optional<Organization> find(UUID id) { return Optional.ofNullable(values.get(id)); }
    }
}

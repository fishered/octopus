package com.accuenergy.octopus.mgmt.application.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.common.tenant.TenantScope;
import com.accuenergy.octopus.mgmt.domain.asset.Facility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FacilityManagementServiceTest {
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final UUID organizationId = UUID.randomUUID();
    private final MemoryFacilityRepository repository = new MemoryFacilityRepository();
    private final FacilityManagementService service = new FacilityManagementService(repository,
            new AuthorizationPolicy(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsPostgisBackedFacilityInsideOrganizationScope() {
        repository.organizationPath = "/root/east";
        var command = new FacilityManagementService.CreateFacility(organizationId, null, "site-a", "Site A",
                "SITE", "America/New_York", "{\"type\":\"Point\",\"coordinates\":[-74,40.7]}");
        TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.create(operator("facility:create", "/root"), command));
        assertEquals("site-a", repository.saved.code());
        assertEquals(tenant.value(), repository.saved.tenantId());
        assertEquals("America/New_York", repository.saved.zoneId().orElseThrow());
    }

    @Test
    void parentMustBelongToSameOrganization() {
        repository.organizationPath = "/root/east";
        repository.parent = new FacilityRepository.ParentContext(UUID.randomUUID());
        var command = new FacilityManagementService.CreateFacility(organizationId, UUID.randomUUID(),
                "room", "Room", "ROOM", null, null);
        assertThrows(IllegalArgumentException.class, () -> TenantContext.run(new TenantScope.Scoped(tenant),
                () -> service.create(operator("facility:create", "/root"), command)));
    }

    @Test
    void spatialQueryStillEnforcesOrganizationScope() throws Exception {
        repository.organizationPath = "/root/east";
        repository.saved = Facility.create(UUID.randomUUID(), tenant.value(), organizationId, null,
                "east", "East", "SITE", null,
                "{\"type\":\"Point\",\"coordinates\":[120,30]}", Instant.parse("2026-01-01T00:00:00Z"));
        var result = TenantContext.call(new TenantScope.Scoped(tenant),
                () -> service.findIntersecting(operator("facility:view", "/root/west"),
                        new FacilityManagementService.SpatialWindow(110, 20, 130, 40)));
        assertTrue(result.isEmpty());
    }

    private AuthenticatedPrincipal operator(String permission, String path) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR, Optional.of(tenant), Set.of(permission),
                Set.of(path), Set.of());
    }

    private static final class MemoryFacilityRepository implements FacilityRepository {
        private String organizationPath;
        private ParentContext parent;
        private Facility saved;
        public boolean existsByCode(String code) { return false; }
        public Optional<String> findOrganizationPath(UUID id) { return Optional.ofNullable(organizationPath); }
        public Optional<ParentContext> findParentContext(UUID id) { return Optional.ofNullable(parent); }
        public void insert(Facility facility) { saved = facility; }
        public Optional<FacilityDetails> findDetails(UUID id) {
            return saved == null ? Optional.empty() : Optional.of(new FacilityDetails(saved, organizationPath));
        }
        public List<FacilityDetails> findIntersecting(double minLon, double minLat, double maxLon, double maxLat) {
            return saved == null ? List.of() : List.of(new FacilityDetails(saved, organizationPath));
        }
    }
}

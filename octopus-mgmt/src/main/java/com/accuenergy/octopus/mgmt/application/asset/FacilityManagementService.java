package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.security.AuthorizationPolicy;
import com.accuenergy.octopus.common.security.ProtectedResource;
import com.accuenergy.octopus.common.security.ResourceAction;
import com.accuenergy.octopus.common.tenant.TenantContext;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.domain.asset.Facility;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FacilityManagementService {
    private final FacilityRepository facilities;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public FacilityManagementService(FacilityRepository facilities,
                                     AuthorizationPolicy authorization, Clock clock) {
        this.facilities = facilities;
        this.authorization = authorization;
        this.clock = clock;
    }

    public Facility create(AuthenticatedPrincipal principal, CreateFacility command) {
        TenantId tenantId = currentTenant();
        String organizationPath = facilities.findOrganizationPath(command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization"));
        if (!authorization.isAllowed(principal, "facility:create", ResourceAction.CONFIGURE,
                new ProtectedResource(tenantId, "organization", command.organizationId(),
                        Optional.of(organizationPath)))) {
            throw new FacilityAccessDeniedException();
        }
        if (command.parentId() != null) {
            FacilityRepository.ParentContext parent = facilities.findParentContext(command.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parent facility"));
            if (!parent.organizationId().equals(command.organizationId())) {
                throw new IllegalArgumentException("Parent facility belongs to another organization");
            }
        }
        if (facilities.existsByCode(command.code())) {
            throw new IllegalArgumentException("Facility code already exists");
        }
        Facility facility = Facility.create(UUID.randomUUID(), tenantId.value(), command.organizationId(),
                command.parentId(), command.code(), command.displayName(), command.facilityType(),
                command.zoneId(), command.geometryGeoJson(), clock.instant());
        facilities.insert(facility);
        return facility;
    }

    public Facility get(AuthenticatedPrincipal principal, UUID facilityId) {
        FacilityRepository.FacilityDetails details = facilities.findDetails(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility"));
        if (!authorization.isAllowed(principal, "facility:view", ResourceAction.VIEW,
                new ProtectedResource(new TenantId(details.facility().tenantId()), "facility", facilityId,
                        Optional.of(details.organizationPath())))) {
            throw new FacilityAccessDeniedException();
        }
        return details.facility();
    }

    public List<Facility> findIntersecting(AuthenticatedPrincipal principal, SpatialWindow window) {
        TenantId tenantId = currentTenant();
        validateWindow(window);
        return facilities.findIntersecting(window.minLongitude(), window.minLatitude(),
                        window.maxLongitude(), window.maxLatitude()).stream()
                .filter(details -> authorization.isAllowed(principal, "facility:view", ResourceAction.VIEW,
                        new ProtectedResource(tenantId, "facility", details.facility().id(),
                                Optional.of(details.organizationPath()))))
                .map(FacilityRepository.FacilityDetails::facility)
                .toList();
    }

    private static void validateWindow(SpatialWindow window) {
        if (window.minLongitude() < -180 || window.minLongitude() > 180
                || window.maxLongitude() < -180 || window.maxLongitude() > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        if (window.minLatitude() < -90 || window.maxLatitude() > 90
                || window.minLatitude() >= window.maxLatitude()) {
            throw new IllegalArgumentException("Latitude window is invalid");
        }
    }

    private static TenantId currentTenant() {
        return TenantContext.requireCurrent().tenantId()
                .orElseThrow(() -> new IllegalStateException("A concrete tenant scope is required"));
    }

    public record CreateFacility(UUID organizationId, UUID parentId, String code, String displayName,
                                 String facilityType, String zoneId, String geometryGeoJson) { }
    public record SpatialWindow(double minLongitude, double minLatitude,
                                double maxLongitude, double maxLatitude) { }
}

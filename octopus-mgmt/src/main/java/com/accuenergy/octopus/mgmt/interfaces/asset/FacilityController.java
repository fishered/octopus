package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.asset.FacilityManagementService;
import com.accuenergy.octopus.mgmt.domain.asset.Facility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities")
public final class FacilityController {
    private static final Set<String> GEOMETRY_TYPES = Set.of("Point", "MultiPoint", "LineString",
            "MultiLineString", "Polygon", "MultiPolygon", "GeometryCollection");
    private final FacilityManagementService facilities;
    private final ObjectMapper objectMapper;

    public FacilityController(FacilityManagementService facilities, ObjectMapper objectMapper) {
        this.facilities = facilities;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_facility:create') or hasAuthority('PERM_platform:all')")
    public FacilityResponse create(Authentication authentication,
                                   @Valid @RequestBody CreateFacilityRequest request) {
        String geometry = validateGeometry(request.geometry());
        Facility facility = facilities.create(principal(authentication),
                new FacilityManagementService.CreateFacility(request.organizationId(), request.parentId(),
                        request.code(), request.displayName(), request.facilityType(), request.zoneId(), geometry));
        return response(facility);
    }

    @GetMapping("/{facilityId}")
    @PreAuthorize("hasAuthority('PERM_facility:view') or hasAuthority('PERM_platform:all')")
    public FacilityResponse get(Authentication authentication, @PathVariable UUID facilityId) {
        return response(facilities.get(principal(authentication), facilityId));
    }

    @GetMapping("/spatial")
    @PreAuthorize("hasAuthority('PERM_facility:view') or hasAuthority('PERM_platform:all')")
    public List<FacilityResponse> spatial(Authentication authentication,
                                          @RequestParam double minLongitude,
                                          @RequestParam double minLatitude,
                                          @RequestParam double maxLongitude,
                                          @RequestParam double maxLatitude) {
        return facilities.findIntersecting(principal(authentication),
                        new FacilityManagementService.SpatialWindow(minLongitude, minLatitude,
                                maxLongitude, maxLatitude)).stream()
                .map(this::response)
                .toList();
    }

    private String validateGeometry(JsonNode geometry) {
        if (geometry == null || geometry.isNull()) return null;
        if (!geometry.isObject() || !geometry.path("type").isTextual()
                || !GEOMETRY_TYPES.contains(geometry.path("type").textValue())) {
            throw new IllegalArgumentException("geometry must be a valid GeoJSON Geometry object");
        }
        boolean collection = "GeometryCollection".equals(geometry.path("type").textValue());
        if (collection && !geometry.path("geometries").isArray()) {
            throw new IllegalArgumentException("GeoJSON GeometryCollection requires geometries");
        }
        if (!collection && !geometry.has("coordinates")) {
            throw new IllegalArgumentException("GeoJSON geometry requires coordinates");
        }
        return geometry.toString();
    }

    private FacilityResponse response(Facility facility) {
        return new FacilityResponse(facility.id(), facility.organizationId(), facility.parentId().orElse(null),
                facility.code(), facility.displayName(), facility.facilityType(), facility.zoneId().orElse(null),
                facility.geometryGeoJson().map(this::readJson).orElse(null), facility.status(), facility.version(),
                facility.createdAt(), facility.updatedAt());
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException invalidStoredGeometry) {
            throw new IllegalStateException("Stored facility geometry is invalid", invalidStoredGeometry);
        }
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateFacilityRequest(@NotNull UUID organizationId, UUID parentId,
                                        @NotBlank String code, @NotBlank String displayName,
                                        @NotBlank String facilityType, String zoneId, JsonNode geometry) { }

    public record FacilityResponse(UUID id, UUID organizationId, UUID parentId, String code,
                                   String displayName, String facilityType, String zoneId, JsonNode geometry,
                                   Facility.Status status, long version, Instant createdAt, Instant updatedAt) { }
}

package com.accuenergy.octopus.mgmt.interfaces.organization;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.organization.OrganizationManagementService;
import com.accuenergy.octopus.mgmt.domain.organization.Organization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public final class OrganizationController {
    private final OrganizationManagementService organizations;
    public OrganizationController(OrganizationManagementService organizations) { this.organizations = organizations; }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_organization:create') or hasAuthority('PERM_platform:all')")
    public OrganizationResponse create(Authentication authentication,
                                       @Valid @RequestBody CreateOrganizationRequest request) {
        return response(organizations.create(principal(authentication),
                new OrganizationManagementService.CreateOrganization(request.parentId(), request.code(),
                        request.displayName(), request.zoneId())));
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('PERM_organization:view') or hasAuthority('PERM_platform:all')")
    public OrganizationResponse get(Authentication authentication, @PathVariable UUID organizationId) {
        return response(organizations.get(principal(authentication), organizationId));
    }

    private static OrganizationResponse response(Organization organization) {
        return new OrganizationResponse(organization.id(), organization.parentId().orElse(null),
                organization.path(), organization.code(), organization.displayName(),
                organization.zoneId().orElse(null), organization.status(), organization.version(),
                organization.createdAt(), organization.updatedAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record CreateOrganizationRequest(UUID parentId, @NotBlank String code,
                                            @NotBlank String displayName, String zoneId) { }
    public record OrganizationResponse(UUID id, UUID parentId, String path, String code,
                                       String displayName, String zoneId, Organization.Status status,
                                       long version, Instant createdAt, Instant updatedAt) { }
}

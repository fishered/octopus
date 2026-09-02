package com.accuenergy.octopus.mgmt.application.organization;

import com.accuenergy.octopus.mgmt.domain.organization.Organization;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    boolean existsByCode(String code);
    void insert(Organization organization);
    Optional<Organization> find(UUID organizationId);
}

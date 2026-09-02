package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.mgmt.domain.asset.Facility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityRepository {
    boolean existsByCode(String code);
    Optional<String> findOrganizationPath(UUID organizationId);
    Optional<ParentContext> findParentContext(UUID parentFacilityId);
    void insert(Facility facility);
    Optional<FacilityDetails> findDetails(UUID facilityId);
    List<FacilityDetails> findIntersecting(double minLongitude, double minLatitude,
                                           double maxLongitude, double maxLatitude);

    record ParentContext(UUID organizationId) { }
    record FacilityDetails(Facility facility, String organizationPath) { }
}

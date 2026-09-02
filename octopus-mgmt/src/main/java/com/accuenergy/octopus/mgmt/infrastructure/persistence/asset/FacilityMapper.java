package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FacilityMapper extends BaseMapper<FacilityEntity> {
    int insertFacility(@Param("facility") FacilityEntity facility);
    String findOrganizationPath(@Param("organizationId") UUID organizationId);
    ParentContextRow findParentContext(@Param("parentFacilityId") UUID parentFacilityId);
    FacilityDetailsRow findDetails(@Param("facilityId") UUID facilityId);
    List<FacilityDetailsRow> findIntersecting(@Param("minLongitude") double minLongitude,
                                              @Param("minLatitude") double minLatitude,
                                              @Param("maxLongitude") double maxLongitude,
                                              @Param("maxLatitude") double maxLatitude);

    record ParentContextRow(UUID organizationId) { }
    record FacilityDetailsRow(UUID id, UUID tenantId, UUID organizationId, UUID parentId,
                              String code, String displayName, String facilityType, String zoneId,
                              String geometryGeoJson, String status, long version,
                              Instant createdAt, Instant updatedAt, String organizationPath) { }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.accuenergy.octopus.mgmt.application.asset.FacilityRepository;
import com.accuenergy.octopus.mgmt.domain.asset.Facility;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisFacilityRepository implements FacilityRepository {
    private final FacilityMapper mapper;

    public MybatisFacilityRepository(FacilityMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<FacilityEntity>lambdaQuery().eq(FacilityEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<String> findOrganizationPath(UUID organizationId) {
        return Optional.ofNullable(mapper.findOrganizationPath(organizationId));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ParentContext> findParentContext(UUID parentFacilityId) {
        return Optional.ofNullable(mapper.findParentContext(parentFacilityId))
                .map(row -> new ParentContext(row.organizationId()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(Facility facility) {
        if (mapper.insertFacility(toEntity(facility)) != 1) {
            throw new IllegalStateException("Unable to insert facility");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<FacilityDetails> findDetails(UUID facilityId) {
        return Optional.ofNullable(mapper.findDetails(facilityId)).map(MybatisFacilityRepository::toDetails);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<FacilityDetails> findIntersecting(double minLongitude, double minLatitude,
                                                   double maxLongitude, double maxLatitude) {
        return mapper.findIntersecting(minLongitude, minLatitude, maxLongitude, maxLatitude).stream()
                .map(MybatisFacilityRepository::toDetails)
                .toList();
    }

    private static FacilityDetails toDetails(FacilityMapper.FacilityDetailsRow row) {
        Facility facility = Facility.restore(row.id(), row.tenantId(), row.organizationId(), row.parentId(),
                row.code(), row.displayName(), row.facilityType(), row.zoneId(), row.geometryGeoJson(),
                Facility.Status.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
        return new FacilityDetails(facility, row.organizationPath());
    }

    private static FacilityEntity toEntity(Facility facility) {
        FacilityEntity entity = new FacilityEntity();
        entity.setId(facility.id()); entity.setTenantId(facility.tenantId());
        entity.setOrganizationId(facility.organizationId()); entity.setParentId(facility.parentId().orElse(null));
        entity.setCode(facility.code()); entity.setDisplayName(facility.displayName());
        entity.setFacilityType(facility.facilityType()); entity.setZoneId(facility.zoneId().orElse(null));
        entity.setGeometryGeoJson(facility.geometryGeoJson().orElse(null)); entity.setStatus(facility.status().name());
        entity.setVersion(facility.version()); entity.setCreatedAt(facility.createdAt()); entity.setUpdatedAt(facility.updatedAt());
        return entity;
    }
}

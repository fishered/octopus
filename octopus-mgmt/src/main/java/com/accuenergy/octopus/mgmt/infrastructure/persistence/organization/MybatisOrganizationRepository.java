package com.accuenergy.octopus.mgmt.infrastructure.persistence.organization;

import com.accuenergy.octopus.mgmt.application.organization.OrganizationRepository;
import com.accuenergy.octopus.mgmt.domain.organization.Organization;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisOrganizationRepository implements OrganizationRepository {
    private final OrganizationMapper mapper;

    public MybatisOrganizationRepository(OrganizationMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<OrganizationEntity>lambdaQuery()
                .eq(OrganizationEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(Organization organization) {
        if (mapper.insert(toEntity(organization)) != 1) {
            throw new IllegalStateException("Unable to insert organization");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<Organization> find(UUID organizationId) {
        return Optional.ofNullable(mapper.selectById(organizationId)).map(MybatisOrganizationRepository::toDomain);
    }

    private static OrganizationEntity toEntity(Organization organization) {
        OrganizationEntity entity = new OrganizationEntity();
        entity.setId(organization.id()); entity.setTenantId(organization.tenantId());
        entity.setParentId(organization.parentId().orElse(null)); entity.setPath(organization.path());
        entity.setCode(organization.code()); entity.setDisplayName(organization.displayName());
        entity.setZoneId(organization.zoneId().orElse(null)); entity.setStatus(organization.status().name());
        entity.setVersion(organization.version()); entity.setCreatedAt(organization.createdAt());
        entity.setUpdatedAt(organization.updatedAt());
        return entity;
    }

    private static Organization toDomain(OrganizationEntity entity) {
        return Organization.restore(entity.getId(), entity.getTenantId(), entity.getParentId(), entity.getPath(),
                entity.getCode(), entity.getDisplayName(), entity.getZoneId(),
                Organization.Status.valueOf(entity.getStatus()), entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

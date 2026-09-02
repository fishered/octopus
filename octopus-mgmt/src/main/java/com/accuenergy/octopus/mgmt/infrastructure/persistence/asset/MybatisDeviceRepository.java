package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceRepository;
import com.accuenergy.octopus.mgmt.domain.asset.Device;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceRepository implements DeviceRepository {
    private final DeviceMapper mapper;

    public MybatisDeviceRepository(DeviceMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<DeviceEntity>lambdaQuery().eq(DeviceEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(Device device) {
        if (mapper.insert(toEntity(device)) != 1) throw new IllegalStateException("Unable to insert device");
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceDetails> findDetails(UUID deviceId) {
        return Optional.ofNullable(mapper.findDetails(deviceId)).map(this::toDetails);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<String> findOrganizationPath(UUID organizationId) {
        return Optional.ofNullable(mapper.findOrganizationPath(organizationId));
    }

    private DeviceDetails toDetails(DeviceMapper.DeviceDetailsRow row) {
        Device device = Device.restore(row.id(), row.tenantId(), row.organizationId(), row.facilityId(),
                row.deviceTypeId(), row.thingModelId(), row.modelVersion(), row.code(), row.displayName(),
                Device.Status.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
        return new DeviceDetails(device, row.organizationPath());
    }

    private static DeviceEntity toEntity(Device device) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(device.id()); entity.setTenantId(device.tenantId()); entity.setOrganizationId(device.organizationId());
        entity.setFacilityId(device.facilityId().orElse(null)); entity.setDeviceTypeId(device.deviceTypeId());
        entity.setThingModelId(device.thingModelId()); entity.setModelVersion(device.modelVersion());
        entity.setCode(device.code()); entity.setDisplayName(device.displayName()); entity.setStatus(device.status().name());
        entity.setVersion(device.version()); entity.setCreatedAt(device.createdAt()); entity.setUpdatedAt(device.updatedAt());
        return entity;
    }
}


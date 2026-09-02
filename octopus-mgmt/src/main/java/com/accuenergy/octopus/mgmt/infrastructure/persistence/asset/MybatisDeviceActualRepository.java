package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceActualRepository;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceActual;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceActualRepository implements DeviceActualRepository {
    private final DeviceActualMapper mapper;

    public MybatisDeviceActualRepository(DeviceActualMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceContext> findDeviceContext(UUID deviceId) {
        return Optional.ofNullable(mapper.findDeviceContext(deviceId))
                .map(row -> new DeviceContext(row.tenantId(), row.organizationPath()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean hardwareSerialExists(String hardwareSerial) {
        return mapper.selectCount(Wrappers.<DeviceActualEntity>lambdaQuery()
                .eq(DeviceActualEntity::getHardwareSerial, hardwareSerial)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean deviceAlreadyCommissioned(UUID deviceId) {
        return mapper.selectCount(Wrappers.<DeviceActualEntity>lambdaQuery()
                .eq(DeviceActualEntity::getDeviceId, deviceId)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean certificateInUse(UUID certificateId, UUID excludingActualId) {
        return mapper.countCertificateUse(certificateId, excludingActualId) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(DeviceActual actual) {
        if (mapper.insert(toEntity(actual)) != 1) throw new IllegalStateException("Unable to insert device hardware");
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void update(DeviceActual actual) {
        if (mapper.updateById(toEntity(actual)) != 1) {
            throw new IllegalStateException("Device hardware update was concurrent or missing");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceActualDetails> findDetailsByDeviceId(UUID deviceId) {
        return Optional.ofNullable(mapper.findDetailsByDeviceId(deviceId)).map(row -> {
            DeviceActual actual = DeviceActual.restore(row.id(), row.tenantId(), row.deviceId(),
                    row.hardwareSerial(), row.manufacturer(), row.firmwareVersion(), row.certificateId(),
                    DeviceActual.ConnectivityStatus.valueOf(row.connectivityStatus()), row.lastSeenAt(),
                    row.commissionedAt(), row.version(), row.createdAt(), row.updatedAt());
            return new DeviceActualDetails(actual, row.organizationPath());
        });
    }

    private static DeviceActualEntity toEntity(DeviceActual actual) {
        DeviceActualEntity entity = new DeviceActualEntity();
        entity.setId(actual.id()); entity.setTenantId(actual.tenantId()); entity.setDeviceId(actual.deviceId());
        entity.setHardwareSerial(actual.hardwareSerial()); entity.setManufacturer(actual.manufacturer().orElse(null));
        entity.setFirmwareVersion(actual.firmwareVersion().orElse(null));
        entity.setCertificateId(actual.certificateId().orElse(null));
        entity.setConnectivityStatus(actual.connectivityStatus().name());
        entity.setLastSeenAt(actual.lastSeenAt().orElse(null)); entity.setCommissionedAt(actual.commissionedAt());
        entity.setVersion(actual.version()); entity.setCreatedAt(actual.createdAt()); entity.setUpdatedAt(actual.updatedAt());
        return entity;
    }
}

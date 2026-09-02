package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceShadowRepository;
import com.accuenergy.octopus.mgmt.domain.monitoring.DeviceShadow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceShadowRepository implements DeviceShadowRepository {
    private final DeviceShadowMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisDeviceShadowRepository(DeviceShadowMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void apply(DeviceShadowReported event) {
        validateState(event.stateJson());
        if (mapper.insertInbox(event.tenantId(), event.eventId(), event.deviceId(), event.receivedAt()) != 1) return;
        DeviceShadowMapper.ShadowRow row = mapper.findForUpdate(event.deviceId());
        if (row == null) {
            if (mapper.insertProjection(toRow(DeviceShadow.from(event))) != 1) {
                throw new IllegalStateException("Unable to insert device shadow projection");
            }
            return;
        }
        DeviceShadow shadow = toDomain(row);
        if (shadow.apply(event)
                && mapper.updateProjection(toRow(shadow), shadow.projectionVersion() - 1) != 1) {
            throw new IllegalStateException("Device shadow projection was concurrently modified");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceShadow> find(java.util.UUID deviceId) {
        return Optional.ofNullable(mapper.find(deviceId)).map(MybatisDeviceShadowRepository::toDomain);
    }

    private void validateState(String stateJson) {
        try {
            var node = objectMapper.readTree(stateJson);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Shadow state must be a JSON object");
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("Shadow state is invalid JSON", invalidJson);
        }
    }

    private static DeviceShadow toDomain(DeviceShadowMapper.ShadowRow row) {
        return DeviceShadow.restore(row.tenantId(), row.deviceId(), row.shadowVersion(), row.stateJson(),
                row.reportedAt(), row.receivedAt(), row.appliedDesiredVersion(), row.projectionVersion());
    }

    private static DeviceShadowMapper.ShadowRow toRow(DeviceShadow shadow) {
        return new DeviceShadowMapper.ShadowRow(shadow.tenantId(), shadow.deviceId(), shadow.shadowVersion(),
                shadow.stateJson(), shadow.reportedAt(), shadow.receivedAt(),
                shadow.appliedDesiredVersion().orElse(null), shadow.projectionVersion(), null);
    }
}

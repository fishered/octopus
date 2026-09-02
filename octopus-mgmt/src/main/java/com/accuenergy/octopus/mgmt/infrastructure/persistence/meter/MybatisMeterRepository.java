package com.accuenergy.octopus.mgmt.infrastructure.persistence.meter;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.mgmt.application.meter.MeterRepository;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisMeterRepository implements MeterRepository {
    private final MeterMapper mapper;
    private final ObjectMapper objectMapper;
    private final String meterConfigurationTopic;

    public MybatisMeterRepository(MeterMapper mapper, ObjectMapper objectMapper,
            @Value("${octopus.kafka.meter-configuration-topic:octopus.local.catalog.meter-configuration.v1}")
            String meterConfigurationTopic) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.meterConfigurationTopic = meterConfigurationTopic;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<MeterEntity>lambdaQuery().eq(MeterEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceContext> findDeviceContext(UUID deviceId) {
        return Optional.ofNullable(mapper.findDeviceContext(deviceId))
                .map(row -> new DeviceContext(row.tenantId(), row.organizationPath(), row.modelVersion()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ParameterConfiguration> findParameterConfiguration(UUID parameterId, UUID unitId) {
        return Optional.ofNullable(mapper.findParameterConfiguration(parameterId, unitId))
                .map(row -> new ParameterConfiguration(ValueSemantics.valueOf(row.semantics()),
                        row.sourceUnitCode(), row.canonicalUnitCode(), row.scale(), row.offset(),
                        row.defaultDecimalScale()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean isBoundToDeviceModel(UUID deviceId, UUID parameterId, UUID unitId) {
        return mapper.countBinding(deviceId, parameterId, unitId) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean facilityExists(UUID facilityId) { return mapper.countFacility(facilityId) > 0; }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insert(Meter meter, MeterConfigurationChanged event) {
        if (mapper.insertMeter(toEntity(meter)) != 1) throw new IllegalStateException("Unable to insert meter");
        if (!meter.id().equals(event.meterId()) || !meter.tenantId().equals(event.tenantId())) {
            throw new IllegalArgumentException("Meter configuration event does not match the aggregate");
        }
        if (mapper.insertOutbox(event.eventId(), event.tenantId(), meterConfigurationTopic,
                event.meterId().toString(), MeterConfigurationChanged.class.getSimpleName(),
                serialize(event), event.occurredAt()) != 1) {
            throw new IllegalStateException("Unable to append meter configuration event");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<MeterDetails> findDetails(UUID meterId) {
        return Optional.ofNullable(mapper.findDetails(meterId)).map(row -> {
            Meter meter = Meter.restore(row.id(), row.tenantId(), row.deviceId(), row.facilityId(),
                    row.parameterId(), row.unitId(), row.code(), row.displayName(),
                    Meter.Kind.valueOf(row.meterKind()), row.calculationExpression(), row.rolloverModulus(),
                    row.decimalScale(), Meter.Status.valueOf(row.status()), row.version(), row.createdAt(),
                    row.updatedAt(), ValueSemantics.valueOf(row.semantics()));
            return new MeterDetails(meter, row.organizationPath(), row.canonicalUnitCode(),
                    row.effectiveZoneId());
        });
    }

    private static MeterEntity toEntity(Meter meter) {
        MeterEntity entity = new MeterEntity();
        entity.setId(meter.id()); entity.setTenantId(meter.tenantId()); entity.setDeviceId(meter.deviceId());
        entity.setFacilityId(meter.facilityId().orElse(null)); entity.setParameterId(meter.parameterId());
        entity.setUnitId(meter.unitId()); entity.setCode(meter.code()); entity.setDisplayName(meter.displayName());
        entity.setMeterKind(meter.kind().name()); entity.setCalculationExpression(meter.calculationExpression().orElse(null));
        entity.setRolloverModulus(meter.rolloverModulus().orElse(null)); entity.setDecimalScale(meter.decimalScale());
        entity.setStatus(meter.status().name()); entity.setVersion(meter.version());
        entity.setCreatedAt(meter.createdAt()); entity.setUpdatedAt(meter.updatedAt());
        return entity;
    }

    private String serialize(MeterConfigurationChanged event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Unable to serialize meter configuration event", serializationFailure);
        }
    }
}

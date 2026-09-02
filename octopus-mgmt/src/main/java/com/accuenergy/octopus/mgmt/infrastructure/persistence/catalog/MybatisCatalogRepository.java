package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import com.accuenergy.octopus.mgmt.application.catalog.CatalogRepository;
import com.accuenergy.octopus.mgmt.domain.catalog.DeviceType;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.domain.catalog.ThingModel;
import com.accuenergy.octopus.mgmt.domain.catalog.UnitDefinition;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisCatalogRepository implements CatalogRepository {
    private final DeviceTypeMapper deviceTypes;
    private final UnitMapper units;
    private final ParameterDefinitionMapper parameters;
    private final ThingModelMapper models;

    public MybatisCatalogRepository(DeviceTypeMapper deviceTypes, UnitMapper units,
                                    ParameterDefinitionMapper parameters,
                                    ThingModelMapper models) {
        this.deviceTypes = deviceTypes;
        this.units = units;
        this.parameters = parameters;
        this.models = models;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean deviceTypeCodeExists(String code) {
        return deviceTypes.countCode(code) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insertDeviceType(DeviceType deviceType) {
        if (deviceTypes.insertType(toEntity(deviceType)) != 1) {
            throw new IllegalStateException("Unable to insert device type");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceType> findDeviceType(UUID deviceTypeId) {
        DeviceTypeEntity entity = deviceTypes.findType(deviceTypeId);
        if (entity == null) return Optional.empty();
        return Optional.of(DeviceType.restore(entity.getId(), entity.getTenantId(), entity.getCode(),
                entity.getDisplayName(), entity.getCapabilitiesDocument(),
                DeviceType.Status.valueOf(entity.getStatus()), entity.getCreatedAt()));
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public boolean unitCodeExists(String code) {
        return units.selectCount(Wrappers.<UnitEntity>lambdaQuery().eq(UnitEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void insertUnit(UnitDefinition unit) {
        UnitEntity entity = new UnitEntity();
        entity.setId(unit.id()); entity.setCode(unit.code()); entity.setSymbol(unit.symbol());
        entity.setDimension(unit.dimension()); entity.setScale(unit.scale()); entity.setOffsetValue(unit.offset());
        entity.setDescription(unit.description().orElse(null));
        if (units.insert(entity) != 1) throw new IllegalStateException("Unable to insert unit");
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<UnitDefinition> findUnit(UUID unitId) {
        return Optional.ofNullable(units.selectById(unitId)).map(entity -> new UnitDefinition(entity.getId(),
                entity.getCode(), entity.getSymbol(), entity.getDimension(), entity.getScale(),
                entity.getOffsetValue(), Optional.ofNullable(entity.getDescription())));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean parameterCodeExists(String code) {
        return parameters.selectCount(Wrappers.<ParameterDefinitionEntity>lambdaQuery()
                .eq(ParameterDefinitionEntity::getCode, code)) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insertParameter(ParameterDefinition parameter) {
        ParameterDefinitionEntity entity = toEntity(parameter);
        if (parameters.insert(entity) != 1) throw new IllegalStateException("Unable to insert parameter");
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ParameterDefinition> findParameter(UUID parameterId) {
        return Optional.ofNullable(parameters.selectById(parameterId)).map(MybatisCatalogRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean thingModelVersionExists(String code, long modelVersion) {
        return models.countVersion(code, modelVersion) > 0;
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void insertThingModel(ThingModel model) {
        ThingModelEntity entity = toEntity(model);
        if (models.insertModel(entity) != 1) throw new IllegalStateException("Unable to insert thing model");
        UUID tenantId = model.tenantId().orElse(null);
        for (ThingModel.ParameterBinding binding : model.parameters()) {
            if (models.insertParameter(tenantId, model.id(), binding.parameterId(), binding.unitId(),
                    binding.required(), binding.sortOrder(), binding.accessMode().name()) != 1) {
                throw new IllegalStateException("Unable to insert thing model parameter");
            }
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<ThingModel> findThingModel(UUID thingModelId) {
        ThingModelEntity entity = models.findModel(thingModelId);
        if (entity == null) return Optional.empty();
        var bindings = models.findParameters(thingModelId).stream()
                .map(row -> new ThingModel.ParameterBinding(row.parameterId(), row.unitId(),
                        row.required(), row.sortOrder(), ThingModel.AccessMode.valueOf(row.accessMode())))
                .toList();
        return Optional.of(ThingModel.restore(entity.getId(), entity.getTenantId(), entity.getDeviceTypeId(),
                entity.getCode(), entity.getDisplayName(), entity.getModelVersion(), entity.getSchemaDocument(),
                bindings, ThingModel.Status.valueOf(entity.getStatus()), entity.getPublishedAt(), entity.getCreatedAt()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void updateThingModelLifecycle(ThingModel model) {
        if (models.updateLifecycle(toEntity(model)) != 1) {
            throw new IllegalStateException("Thing model lifecycle update was concurrent or missing");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean unitsHaveSameDimension(UUID firstUnitId, UUID secondUnitId) {
        return models.countSameDimension(firstUnitId, secondUnitId) > 0;
    }

    private static ParameterDefinitionEntity toEntity(ParameterDefinition parameter) {
        ParameterDefinitionEntity entity = new ParameterDefinitionEntity();
        entity.setId(parameter.id()); entity.setTenantId(parameter.tenantId().orElse(null));
        entity.setCode(parameter.code()); entity.setDisplayName(parameter.displayName());
        entity.setQuantityKind(parameter.quantityKind()); entity.setValueSemantics(parameter.valueSemantics().name());
        entity.setDataType(parameter.dataType().name()); entity.setCanonicalUnitId(parameter.canonicalUnitId().orElse(null));
        entity.setDecimalScale(parameter.decimalScale().orElse(null)); entity.setStatus(parameter.status().name());
        return entity;
    }

    private static DeviceTypeEntity toEntity(DeviceType deviceType) {
        DeviceTypeEntity entity = new DeviceTypeEntity();
        entity.setId(deviceType.id()); entity.setTenantId(deviceType.tenantId().orElse(null));
        entity.setCode(deviceType.code()); entity.setDisplayName(deviceType.displayName());
        entity.setCapabilitiesDocument(deviceType.capabilitiesDocument());
        entity.setStatus(deviceType.status().name()); entity.setCreatedAt(deviceType.createdAt());
        return entity;
    }

    private static ParameterDefinition toDomain(ParameterDefinitionEntity entity) {
        return new ParameterDefinition(entity.getId(), Optional.ofNullable(entity.getTenantId()), entity.getCode(),
                entity.getDisplayName(), entity.getQuantityKind(),
                ParameterDefinition.ValueSemantics.valueOf(entity.getValueSemantics()),
                ParameterDefinition.DataType.valueOf(entity.getDataType()),
                Optional.ofNullable(entity.getCanonicalUnitId()), Optional.ofNullable(entity.getDecimalScale()),
                ParameterDefinition.Status.valueOf(entity.getStatus()));
    }

    private static ThingModelEntity toEntity(ThingModel model) {
        ThingModelEntity entity = new ThingModelEntity();
        entity.setId(model.id()); entity.setTenantId(model.tenantId().orElse(null));
        entity.setDeviceTypeId(model.deviceTypeId()); entity.setCode(model.code());
        entity.setDisplayName(model.displayName()); entity.setModelVersion(model.modelVersion());
        entity.setSchemaDocument(model.schemaDocument()); entity.setStatus(model.status().name());
        entity.setPublishedAt(model.publishedAt().orElse(null)); entity.setCreatedAt(model.createdAt());
        return entity;
    }
}

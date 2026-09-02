package com.accuenergy.octopus.mgmt.application.catalog;

import com.accuenergy.octopus.mgmt.domain.catalog.DeviceType;
import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition;
import com.accuenergy.octopus.mgmt.domain.catalog.ThingModel;
import com.accuenergy.octopus.mgmt.domain.catalog.UnitDefinition;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository {
    boolean deviceTypeCodeExists(String code);
    void insertDeviceType(DeviceType deviceType);
    Optional<DeviceType> findDeviceType(UUID deviceTypeId);

    boolean unitCodeExists(String code);
    void insertUnit(UnitDefinition unit);
    Optional<UnitDefinition> findUnit(UUID unitId);

    boolean parameterCodeExists(String code);
    void insertParameter(ParameterDefinition parameter);
    Optional<ParameterDefinition> findParameter(UUID parameterId);

    boolean thingModelVersionExists(String code, long modelVersion);
    void insertThingModel(ThingModel model);
    Optional<ThingModel> findThingModel(UUID thingModelId);
    void updateThingModelLifecycle(ThingModel model);

    boolean unitsHaveSameDimension(UUID firstUnitId, UUID secondUnitId);
}

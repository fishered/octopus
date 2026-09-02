package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ThingModelMapper {
    int insertModel(@Param("model") ThingModelEntity model);
    int insertParameter(@Param("tenantId") UUID tenantId, @Param("thingModelId") UUID thingModelId,
                        @Param("parameterId") UUID parameterId, @Param("unitId") UUID unitId,
                        @Param("required") boolean required, @Param("sortOrder") int sortOrder,
                        @Param("accessMode") String accessMode);
    ThingModelEntity findModel(@Param("thingModelId") UUID thingModelId);
    List<ParameterBindingRow> findParameters(@Param("thingModelId") UUID thingModelId);
    int updateLifecycle(@Param("model") ThingModelEntity model);
    long countVersion(@Param("code") String code, @Param("modelVersion") long modelVersion);
    long countSameDimension(@Param("firstUnitId") UUID firstUnitId, @Param("secondUnitId") UUID secondUnitId);

    record ParameterBindingRow(UUID parameterId, UUID unitId, boolean required, int sortOrder,
            String accessMode) { }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.meter;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MeterMapper extends BaseMapper<MeterEntity> {
    int insertMeter(@Param("meter") MeterEntity meter);
    int insertOutbox(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                     @Param("topic") String topic, @Param("partitionKey") String partitionKey,
                     @Param("eventType") String eventType, @Param("payload") String payload,
                     @Param("occurredAt") Instant occurredAt);
    DeviceContextRow findDeviceContext(@Param("deviceId") UUID deviceId);
    ParameterConfigurationRow findParameterConfiguration(@Param("parameterId") UUID parameterId,
                                                         @Param("unitId") UUID unitId);
    long countBinding(@Param("deviceId") UUID deviceId, @Param("parameterId") UUID parameterId,
                      @Param("unitId") UUID unitId);
    long countFacility(@Param("facilityId") UUID facilityId);
    MeterDetailsRow findDetails(@Param("meterId") UUID meterId);

    record DeviceContextRow(UUID tenantId, String organizationPath, long modelVersion) { }

    record ParameterConfigurationRow(String semantics, String sourceUnitCode, String canonicalUnitCode,
                                     BigDecimal scale, BigDecimal offset, int defaultDecimalScale) { }

    record MeterDetailsRow(UUID id, UUID tenantId, UUID deviceId, UUID facilityId, UUID parameterId,
                           UUID unitId, String code, String displayName, String meterKind,
                           String calculationExpression, BigDecimal rolloverModulus, int decimalScale,
                           String status, long version, Instant createdAt, Instant updatedAt,
                           String semantics, String organizationPath, String canonicalUnitCode,
                           String effectiveZoneId) { }
}

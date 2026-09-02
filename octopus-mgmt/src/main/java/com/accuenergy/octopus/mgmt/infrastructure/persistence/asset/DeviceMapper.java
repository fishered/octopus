package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceMapper extends BaseMapper<DeviceEntity> {
    DeviceDetailsRow findDetails(@Param("deviceId") UUID deviceId);
    String findOrganizationPath(@Param("organizationId") UUID organizationId);

    record DeviceDetailsRow(UUID id, UUID tenantId, UUID organizationId, UUID facilityId,
            UUID deviceTypeId, UUID thingModelId, long modelVersion, String code, String displayName,
            String status, long version, Instant createdAt, Instant updatedAt, String organizationPath) { }
}


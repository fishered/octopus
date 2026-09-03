package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceConnectorBindingMapper {
    BindingRow find(@Param("deviceId") UUID deviceId);
    int insert(@Param("binding") DeviceConnectorBindingEntity binding);
    int update(@Param("binding") DeviceConnectorBindingEntity binding, @Param("expectedVersion") long expectedVersion);
    record BindingRow(UUID tenantId, UUID deviceId, String pluginId, String codecId, String configRef, String status,
                      long version, Instant createdAt, Instant updatedAt) { }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceShadowMapper {
    ShadowRow find(@Param("deviceId") UUID deviceId);
    ShadowRow findForUpdate(@Param("deviceId") UUID deviceId);
    int insertProjection(ShadowRow shadow);
    int updateProjection(@Param("shadow") ShadowRow shadow,
            @Param("expectedProjectionVersion") long expectedProjectionVersion);
    @InterceptorIgnore(tenantLine = "true")
    int insertInbox(@Param("tenantId") UUID tenantId, @Param("eventId") UUID eventId,
            @Param("deviceId") UUID deviceId, @Param("receivedAt") Instant receivedAt);

    record ShadowRow(UUID tenantId, UUID deviceId, long shadowVersion, String stateJson,
            Instant reportedAt, Instant receivedAt, Long appliedDesiredVersion,
            long projectionVersion, String organizationPath) { }
}

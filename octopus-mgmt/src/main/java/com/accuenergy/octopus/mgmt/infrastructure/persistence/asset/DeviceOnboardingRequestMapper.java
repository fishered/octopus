package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceOnboardingRequestMapper {
    RequestRow findForUpdate(@Param("requestedBy") UUID requestedBy,
            @Param("idempotencyKey") String idempotencyKey);

    int insertClaim(@Param("requestId") UUID requestId, @Param("tenantId") UUID tenantId,
            @Param("requestedBy") UUID requestedBy, @Param("idempotencyKey") String idempotencyKey,
            @Param("requestSha256") String requestSha256, @Param("requestedAt") Instant requestedAt);

    int complete(@Param("requestId") UUID requestId, @Param("deviceId") UUID deviceId,
            @Param("actualId") UUID actualId, @Param("code") String code,
            @Param("hardwareSerial") String hardwareSerial, @Param("pluginId") String pluginId,
            @Param("codecId") String codecId, @Param("bindingVersion") long bindingVersion,
            @Param("deviceCreatedAt") Instant deviceCreatedAt, @Param("completedAt") Instant completedAt);

    record RequestRow(UUID requestId, String requestSha256, String status,
            UUID deviceId, UUID actualId, String code, String hardwareSerial,
            String pluginId, String codecId, Long bindingVersion, Instant deviceCreatedAt) { }
}

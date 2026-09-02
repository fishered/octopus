package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceDesiredShadowMapper {
    DesiredRow findByIdempotency(@Param("requestedBy") UUID requestedBy,
            @Param("idempotencyKey") String idempotencyKey);
    DesiredRow findByIdempotencyForUpdate(@Param("requestedBy") UUID requestedBy,
            @Param("idempotencyKey") String idempotencyKey);
    CurrentRow lockCurrent(@Param("deviceId") UUID deviceId);
    DesiredRow findCurrent(@Param("deviceId") UUID deviceId);
    DesiredRow findByCommandForUpdate(@Param("commandId") UUID commandId);
    DesiredRow findCurrentRequestForUpdate(@Param("deviceId") UUID deviceId);
    int insertRequest(DesiredRow request);
    int insertCurrent(CurrentRow current);
    int updateCurrent(@Param("current") CurrentRow current,
            @Param("expectedVersion") long expectedVersion);
    int markSuperseded(@Param("requestId") UUID requestId, @Param("at") Instant at);
    int updateRequest(@Param("request") DesiredRow request,
            @Param("expectedProjectionVersion") long expectedProjectionVersion);

    record DesiredRow(UUID requestId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, long expectedVersion, long desiredVersion,
            String desiredStateJson, String requestSha256, UUID commandId, String status,
            String failureCode, Instant requestedAt, Instant expiresAt, Instant appliedAt,
            Instant statusUpdatedAt, long projectionVersion) { }

    record CurrentRow(UUID tenantId, UUID deviceId, long desiredVersion, UUID requestId,
            String desiredStateJson, Instant updatedAt) { }
}

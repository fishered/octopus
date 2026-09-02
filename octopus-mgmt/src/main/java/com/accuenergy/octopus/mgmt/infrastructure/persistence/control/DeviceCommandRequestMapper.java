package com.accuenergy.octopus.mgmt.infrastructure.persistence.control;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceCommandRequestMapper {
    CommandRow findByIdempotency(@Param("requestedBy") UUID requestedBy,
            @Param("idempotencyKey") String idempotencyKey);
    CommandRow find(@Param("commandId") UUID commandId);
    CommandRow findForUpdate(@Param("commandId") UUID commandId);
    int insertCommand(DeviceCommandRequestEntity entity);
    int updateCommand(@Param("command") DeviceCommandRequestEntity entity,
            @Param("expectedVersion") long expectedVersion);
    int insertOutbox(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("topic") String topic, @Param("partitionKey") String partitionKey,
            @Param("eventType") String eventType, @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
    @InterceptorIgnore(tenantLine = "true")
    int insertStatusInbox(@Param("tenantId") UUID tenantId, @Param("eventId") UUID eventId,
            @Param("commandId") UUID commandId, @Param("receivedAt") Instant receivedAt);

    record CommandRow(UUID commandId, UUID tenantId, UUID organizationId, UUID deviceId,
            UUID requestedBy, String idempotencyKey, String operation, String payloadSha256,
            String status, String failureCode, Instant requestedAt, Instant expiresAt,
            Instant dispatchedAt, Instant acknowledgedAt, Instant completedAt,
            Instant statusUpdatedAt, long version, String organizationPath) { }
}

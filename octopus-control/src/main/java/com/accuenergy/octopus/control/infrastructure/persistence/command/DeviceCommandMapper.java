package com.accuenergy.octopus.control.infrastructure.persistence.command;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceCommandMapper {
    DeviceCommandEntity find(@Param("tenantId") UUID tenantId, @Param("commandId") UUID commandId);
    int insertCommand(DeviceCommandEntity command);
    int updateCommand(@Param("command") DeviceCommandEntity command,
            @Param("expectedVersion") long expectedVersion);
    int insertStatusOutbox(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
            @Param("topic") String topic, @Param("partitionKey") String partitionKey,
            @Param("payload") String payload, @Param("occurredAt") Instant occurredAt);
    @InterceptorIgnore(tenantLine = "true")
    boolean tryAcquireRelayLock();
    List<OutboxRow> lockPending(@Param("limit") int limit);
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
    int markFailed(@Param("id") UUID id, @Param("lastError") String lastError);

    record OutboxRow(UUID id, String topic, String partitionKey, String payload) { }
}

package com.accuenergy.octopus.mgmt.infrastructure.outbox;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxMapper {
    @InterceptorIgnore(tenantLine = "true")
    boolean tryAcquireRelayLock();
    List<OutboxRow> lockPending(@Param("limit") int limit);
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
    int markFailed(@Param("id") UUID id, @Param("lastError") String lastError);
    int insertOutbox(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("topic") String topic,
            @Param("partitionKey") String partitionKey, @Param("eventType") String eventType, @Param("payload") String payload,
            @Param("occurredAt") Instant occurredAt);
    record OutboxRow(UUID id, String topic, String partitionKey, String payload) { }
}

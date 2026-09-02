package com.accuenergy.octopus.mgmt.infrastructure.persistence.operations;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DltReplayMapper {
    int insert(@Param("id") UUID id, @Param("actorAccountId") UUID actorAccountId,
               @Param("actorSessionId") UUID actorSessionId, @Param("sourceDltTopic") String sourceDltTopic,
               @Param("sourcePartition") int sourcePartition, @Param("sourceOffset") long sourceOffset,
               @Param("sourceTimestamp") Instant sourceTimestamp,
               @Param("sourceKeySha256") String sourceKeySha256, @Param("payloadSize") int payloadSize,
               @Param("destinationTopic") String destinationTopic, @Param("replayAttempt") int replayAttempt,
               @Param("replayRoot") String replayRoot, @Param("reason") String reason,
               @Param("createdAt") Instant createdAt);

    int insertRequestedAudit(@Param("auditId") UUID auditId, @Param("jobId") UUID jobId,
                             @Param("actorAccountId") UUID actorAccountId,
                             @Param("actorSessionId") UUID actorSessionId,
                             @Param("sourceDltTopic") String sourceDltTopic,
                             @Param("sourcePartition") int sourcePartition,
                             @Param("sourceOffset") long sourceOffset,
                             @Param("destinationTopic") String destinationTopic,
                             @Param("replayAttempt") int replayAttempt, @Param("replayRoot") String replayRoot,
                             @Param("reason") String reason, @Param("occurredAt") Instant occurredAt);

    int markSucceeded(@Param("jobId") UUID jobId, @Param("destinationPartition") int destinationPartition,
                      @Param("destinationOffset") long destinationOffset,
                      @Param("destinationTimestamp") Instant destinationTimestamp,
                      @Param("completedAt") Instant completedAt);

    int markFailed(@Param("jobId") UUID jobId, @Param("failureReason") String failureReason,
                   @Param("completedAt") Instant completedAt);

    int insertOutcomeAudit(@Param("auditId") UUID auditId, @Param("jobId") UUID jobId,
                           @Param("eventType") String eventType, @Param("outcome") String outcome,
                           @Param("failureReason") String failureReason,
                           @Param("destinationPartition") Integer destinationPartition,
                           @Param("destinationOffset") Long destinationOffset,
                           @Param("occurredAt") Instant occurredAt);

    ReplayRow find(@Param("jobId") UUID jobId);

    record ReplayRow(UUID id, UUID actorAccountId, UUID actorSessionId,
                     String sourceDltTopic, int sourcePartition, long sourceOffset,
                     Instant sourceTimestamp, String sourceKeySha256, int payloadSize,
                     String destinationTopic, int replayAttempt, String replayRoot, String reason,
                     String status, Integer destinationPartition, Long destinationOffset,
                     Instant destinationTimestamp, String failureReason, Instant createdAt,
                     Instant completedAt) { }
}

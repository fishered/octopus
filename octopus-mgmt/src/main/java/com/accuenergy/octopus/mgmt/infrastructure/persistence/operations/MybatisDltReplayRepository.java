package com.accuenergy.octopus.mgmt.infrastructure.persistence.operations;

import com.accuenergy.octopus.mgmt.application.operations.DltReplayConflictException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayPublisher;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayRepository;
import com.accuenergy.octopus.mgmt.domain.operations.DltReplayJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDltReplayRepository implements DltReplayRepository {
    private final DltReplayMapper mapper;

    public MybatisDltReplayRepository(DltReplayMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void create(DltReplayJob job) {
        try {
            int inserted = mapper.insert(job.id(), job.actorAccountId(), job.actorSessionId(),
                    job.sourceDltTopic(), job.sourcePartition(), job.sourceOffset(), job.sourceTimestamp(),
                    job.sourceKeySha256(), job.payloadSize(), job.destinationTopic(), job.replayAttempt(),
                    job.replayRoot(), job.reason(), job.createdAt());
            if (inserted != 1) throw new IllegalStateException("Unable to create DLT replay job");
        } catch (DuplicateKeyException duplicate) {
            throw new DltReplayConflictException("This exact DLT topic/partition/offset already has a replay job");
        }
        if (mapper.insertRequestedAudit(UUID.randomUUID(), job.id(), job.actorAccountId(), job.actorSessionId(),
                job.sourceDltTopic(), job.sourcePartition(), job.sourceOffset(), job.destinationTopic(),
                job.replayAttempt(), job.replayRoot(), job.reason(), job.createdAt()) != 1) {
            throw new IllegalStateException("Unable to audit DLT replay request");
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void markSucceeded(UUID jobId, DltReplayPublisher.PublishResult result, Instant completedAt) {
        if (mapper.markSucceeded(jobId, result.partition(), result.offset(), result.timestamp(), completedAt) != 1) {
            throw new IllegalStateException("DLT replay job is no longer pending");
        }
        if (mapper.insertOutcomeAudit(UUID.randomUUID(), jobId, "KAFKA_DLT_REPLAY_SUCCEEDED", "SUCCESS",
                null, result.partition(), result.offset(), completedAt) != 1) {
            throw new IllegalStateException("Unable to audit successful DLT replay");
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void markFailed(UUID jobId, String failureReason, Instant completedAt) {
        if (mapper.markFailed(jobId, failureReason, completedAt) != 1) {
            throw new IllegalStateException("DLT replay job is no longer pending");
        }
        if (mapper.insertOutcomeAudit(UUID.randomUUID(), jobId, "KAFKA_DLT_REPLAY_FAILED", "FAILURE",
                failureReason, null, null, completedAt) != 1) {
            throw new IllegalStateException("Unable to audit failed DLT replay");
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<DltReplayJob> find(UUID jobId) {
        return Optional.ofNullable(mapper.find(jobId)).map(MybatisDltReplayRepository::map);
    }

    private static DltReplayJob map(DltReplayMapper.ReplayRow row) {
        return new DltReplayJob(row.id(), row.actorAccountId(), row.actorSessionId(), row.sourceDltTopic(),
                row.sourcePartition(), row.sourceOffset(), row.sourceTimestamp(), row.sourceKeySha256(),
                row.payloadSize(), row.destinationTopic(), row.replayAttempt(), row.replayRoot(), row.reason(),
                DltReplayJob.Status.valueOf(row.status()), Optional.ofNullable(row.destinationPartition()),
                Optional.ofNullable(row.destinationOffset()), Optional.ofNullable(row.destinationTimestamp()),
                Optional.ofNullable(row.failureReason()), row.createdAt(), Optional.ofNullable(row.completedAt()));
    }
}

package com.accuenergy.octopus.mgmt.application.operations;

import com.accuenergy.octopus.mgmt.domain.operations.DltReplayJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DltReplayRepository {
    void create(DltReplayJob job);
    void markSucceeded(UUID jobId, DltReplayPublisher.PublishResult result, Instant completedAt);
    void markFailed(UUID jobId, String failureReason, Instant completedAt);
    Optional<DltReplayJob> find(UUID jobId);
}

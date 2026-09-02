package com.accuenergy.octopus.mgmt.domain.operations;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Platform-wide audit record for one explicitly approved dead-letter replay. */
public record DltReplayJob(
        UUID id,
        UUID actorAccountId,
        UUID actorSessionId,
        String sourceDltTopic,
        int sourcePartition,
        long sourceOffset,
        Instant sourceTimestamp,
        String sourceKeySha256,
        int payloadSize,
        String destinationTopic,
        int replayAttempt,
        String replayRoot,
        String reason,
        Status status,
        Optional<Integer> destinationPartition,
        Optional<Long> destinationOffset,
        Optional<Instant> destinationTimestamp,
        Optional<String> failureReason,
        Instant createdAt,
        Optional<Instant> completedAt) {

    public DltReplayJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Objects.requireNonNull(actorSessionId, "actorSessionId");
        sourceDltTopic = requireText(sourceDltTopic, "sourceDltTopic", 255);
        if (sourcePartition < 0) throw new IllegalArgumentException("sourcePartition must be non-negative");
        if (sourceOffset < 0) throw new IllegalArgumentException("sourceOffset must be non-negative");
        Objects.requireNonNull(sourceTimestamp, "sourceTimestamp");
        sourceKeySha256 = requireText(sourceKeySha256, "sourceKeySha256", 64);
        if (payloadSize < 0) throw new IllegalArgumentException("payloadSize must be non-negative");
        destinationTopic = requireText(destinationTopic, "destinationTopic", 255);
        if (replayAttempt < 1) throw new IllegalArgumentException("replayAttempt must be positive");
        replayRoot = requireText(replayRoot, "replayRoot", 512);
        reason = requireText(reason, "reason", 500);
        Objects.requireNonNull(status, "status");
        destinationPartition = Objects.requireNonNull(destinationPartition, "destinationPartition");
        destinationOffset = Objects.requireNonNull(destinationOffset, "destinationOffset");
        destinationTimestamp = Objects.requireNonNull(destinationTimestamp, "destinationTimestamp");
        failureReason = Objects.requireNonNull(failureReason, "failureReason")
                .map(value -> requireText(value, "failureReason", 1000));
        Objects.requireNonNull(createdAt, "createdAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
        return normalized;
    }

    public enum Status { PENDING, SUCCEEDED, FAILED }
}

package com.accuenergy.octopus.mgmt.application.operations;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.DltRecord;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.RecordHeader;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.SourcePosition;
import com.accuenergy.octopus.mgmt.domain.operations.DltReplayJob;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class DltReplayService {
    public static final String REPLAY_ROOT_HEADER = "octopus-replay-root";
    public static final String REPLAY_ATTEMPT_HEADER = "octopus-replay-attempt";
    public static final String REPLAY_JOB_HEADER = "octopus-replay-job-id";

    private final DltRecordReader reader;
    private final DltReplayPublisher publisher;
    private final DltReplayRepository repository;
    private final Clock clock;
    private final int maximumAttempts;

    public DltReplayService(DltRecordReader reader, DltReplayPublisher publisher,
                            DltReplayRepository repository, Clock clock,
                            @Value("${octopus.kafka.dlt-replay.maximum-attempts:3}") int maximumAttempts) {
        if (maximumAttempts < 1 || maximumAttempts > 100) {
            throw new IllegalArgumentException("DLT replay maximum attempts must be between 1 and 100");
        }
        this.reader = reader;
        this.publisher = publisher;
        this.repository = repository;
        this.clock = clock;
        this.maximumAttempts = maximumAttempts;
    }

    public DltReplayJob replay(AuthenticatedPrincipal principal, SourcePosition position, String reason) {
        requirePlatformAdministrator(principal);
        String normalizedReason = normalizeReason(reason);
        DltRecord record = reader.read(position).orElseThrow(() ->
                new DltReplayNotFoundException("DLT record does not exist or is outside retained offsets"));
        ReplayChain chain = replayChain(record);
        if (chain.previousAttempt() >= maximumAttempts) {
            throw new DltReplayLimitExceededException(maximumAttempts);
        }

        Instant now = clock.instant();
        UUID jobId = UUID.randomUUID();
        DltReplayJob pending = new DltReplayJob(jobId, principal.accountId(), principal.sessionId(),
                position.topic(), position.partition(), position.offset(), record.timestamp(), sha256(record.key()),
                payloadSize(record), record.destinationTopic(), chain.previousAttempt() + 1, chain.root(),
                normalizedReason, DltReplayJob.Status.PENDING, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), now, Optional.empty());
        repository.create(pending);

        DltReplayPublisher.PublishResult result;
        try {
            result = publisher.publish(record, jobId, chain.root(), chain.previousAttempt() + 1);
        } catch (RuntimeException failure) {
            String summary = abbreviated(failure);
            try {
                repository.markFailed(jobId, summary, clock.instant());
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw new DltReplayInfrastructureException("Kafka replay publication failed", failure);
        }

        try {
            Instant completedAt = clock.instant();
            repository.markSucceeded(jobId, result, completedAt);
            return new DltReplayJob(jobId, principal.accountId(), principal.sessionId(),
                    position.topic(), position.partition(), position.offset(), record.timestamp(), sha256(record.key()),
                    payloadSize(record), record.destinationTopic(), chain.previousAttempt() + 1, chain.root(),
                    normalizedReason, DltReplayJob.Status.SUCCEEDED, Optional.of(result.partition()),
                    Optional.of(result.offset()), Optional.of(result.timestamp()), Optional.empty(), now,
                    Optional.of(completedAt));
        } catch (RuntimeException ledgerFailure) {
            throw new DltReplayInfrastructureException(
                    "Replay was published but its audit ledger could not be finalized; do not resubmit the source offset",
                    ledgerFailure);
        }
    }

    public DltReplayJob get(AuthenticatedPrincipal principal, UUID jobId) {
        requirePlatformAdministrator(principal);
        return repository.find(jobId).orElseThrow(() -> new DltReplayNotFoundException("Replay job was not found"));
    }

    private ReplayChain replayChain(DltRecord record) {
        Optional<String> rootHeader = lastHeader(record, REPLAY_ROOT_HEADER);
        Optional<String> attemptHeader = lastHeader(record, REPLAY_ATTEMPT_HEADER);
        String root = rootHeader.orElse(record.position().externalForm()).strip();
        if (root.isEmpty() || root.length() > 512) {
            throw new IllegalArgumentException("DLT replay root header is invalid");
        }
        int previousAttempt = attemptHeader.map(DltReplayService::parseAttempt).orElse(0);
        return new ReplayChain(root, previousAttempt);
    }

    private static Optional<String> lastHeader(DltRecord record, String name) {
        String result = null;
        for (RecordHeader header : record.headers()) {
            if (header.key().equals(name)) {
                if (header.value() == null) throw new IllegalArgumentException(name + " header cannot be null");
                result = new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        return Optional.ofNullable(result);
    }

    private static int parseAttempt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException("negative");
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("DLT replay attempt header is invalid", failure);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw new IllegalArgumentException("Replay reason must contain 1 to 500 characters");
        }
        return reason.strip();
    }

    private static void requirePlatformAdministrator(AuthenticatedPrincipal principal) {
        if (principal.type() != AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN) {
            throw new DltReplayAccessDeniedException();
        }
    }

    private static String sha256(String key) {
        try {
            byte[] value = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static int payloadSize(DltRecord record) {
        byte[] payload = record.value();
        return payload == null ? 0 : payload.length;
    }

    private static String abbreviated(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record ReplayChain(String root, int previousAttempt) { }
}

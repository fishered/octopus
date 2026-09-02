package com.accuenergy.octopus.mgmt.application.operations;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.common.tenant.TenantId;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.DltRecord;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.RecordHeader;
import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.SourcePosition;
import com.accuenergy.octopus.mgmt.domain.operations.DltReplayJob;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DltReplayServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T05:30:00Z");
    private final SourcePosition position = new SourcePosition("telemetry.dlt", 4, 91);
    private final MemoryReader reader = new MemoryReader();
    private final MemoryPublisher publisher = new MemoryPublisher();
    private final MemoryRepository repository = new MemoryRepository();
    private final DltReplayService service = new DltReplayService(reader, publisher, repository,
            Clock.fixed(NOW, ZoneOffset.UTC), 3);

    @Test
    void platformAdministratorReplaysExactBytesAndRecordsConfirmedDestination() {
        byte[] payload = "{malformed-but-immutable}".getBytes(StandardCharsets.UTF_8);
        reader.record = record(payload, List.of(new RecordHeader("trace-id", bytes("trace-1"))));

        DltReplayJob result = service.replay(platform(), position, "Parser deployment has been corrected");

        assertEquals(DltReplayJob.Status.SUCCEEDED, result.status());
        assertEquals("telemetry.raw", result.destinationTopic());
        assertEquals(1, result.replayAttempt());
        assertEquals(position.externalForm(), result.replayRoot());
        assertEquals(4, result.destinationPartition().orElseThrow());
        assertEquals(901, result.destinationOffset().orElseThrow());
        assertArrayEquals(payload, publisher.published.value());
        assertEquals(result.id(), publisher.jobId);
        assertEquals(DltReplayJob.Status.PENDING, repository.created.status());
        assertTrue(repository.succeeded);
        assertFalse(repository.failed);
    }

    @Test
    void replayChainIncrementsAttemptAndRetainsOriginalRoot() {
        reader.record = record(bytes("payload"), List.of(
                new RecordHeader(DltReplayService.REPLAY_ROOT_HEADER, bytes("first.dlt:1:12")),
                new RecordHeader(DltReplayService.REPLAY_ATTEMPT_HEADER, bytes("2"))));

        DltReplayJob result = service.replay(platform(), position, "Third reviewed recovery attempt");

        assertEquals(3, result.replayAttempt());
        assertEquals("first.dlt:1:12", result.replayRoot());
        assertEquals(3, publisher.attempt);
    }

    @Test
    void replayLimitStopsPublicationBeforeCreatingAuditJob() {
        reader.record = record(bytes("payload"), List.of(
                new RecordHeader(DltReplayService.REPLAY_ATTEMPT_HEADER, bytes("3"))));

        assertThrows(DltReplayLimitExceededException.class,
                () -> service.replay(platform(), position, "Would create an uncontrolled loop"));

        assertEquals(null, repository.created);
        assertEquals(null, publisher.published);
    }

    @Test
    void publicationFailureIsDurablyMarkedAndReportedAsUnavailable() {
        reader.record = record(bytes("payload"), List.of());
        publisher.failure = new IllegalStateException("broker unavailable");

        DltReplayInfrastructureException failure = assertThrows(DltReplayInfrastructureException.class,
                () -> service.replay(platform(), position, "Broker recovery test"));

        assertEquals("Kafka replay publication failed", failure.getMessage());
        assertTrue(repository.failed);
        assertFalse(repository.succeeded);
        assertTrue(repository.failureReason.contains("broker unavailable"));
    }

    @Test
    void tenantPrincipalAndMissingOffsetsCannotCreateReplayJobs() {
        assertThrows(DltReplayAccessDeniedException.class,
                () -> service.replay(operator(), position, "Unauthorized tenant replay"));
        assertThrows(DltReplayNotFoundException.class,
                () -> service.replay(platform(), position, "Offset was already retained out"));
        assertEquals(null, repository.created);
    }

    private DltRecord record(byte[] value, List<RecordHeader> headers) {
        return new DltRecord(position, "telemetry.raw", "tenant:device:meter", value,
                NOW.minusSeconds(60), headers);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static AuthenticatedPrincipal platform() {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.PLATFORM_ADMIN, Optional.empty(),
                Set.of("platform:all"), Set.of(), Set.of());
    }

    private static AuthenticatedPrincipal operator() {
        return new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), 0,
                AuthenticatedPrincipal.PrincipalType.OPERATOR,
                Optional.of(new TenantId(UUID.randomUUID())), Set.of("device:operate"), Set.of("/root"), Set.of());
    }

    private final class MemoryReader implements DltRecordReader {
        private DltRecord record;
        @Override public Optional<DltRecord> read(SourcePosition requested) {
            assertEquals(position, requested);
            return Optional.ofNullable(record);
        }
    }

    private static final class MemoryPublisher implements DltReplayPublisher {
        private DltRecord published;
        private UUID jobId;
        private int attempt;
        private RuntimeException failure;

        @Override public PublishResult publish(DltRecord record, UUID replayJobId,
                                               String replayRoot, int replayAttempt) {
            if (failure != null) throw failure;
            published = record;
            jobId = replayJobId;
            attempt = replayAttempt;
            return new PublishResult(record.position().partition(), 901, NOW.plusSeconds(1));
        }
    }

    private static final class MemoryRepository implements DltReplayRepository {
        private DltReplayJob created;
        private boolean succeeded;
        private boolean failed;
        private String failureReason;

        @Override public void create(DltReplayJob job) { created = job; }
        @Override public void markSucceeded(UUID jobId, DltReplayPublisher.PublishResult result,
                                            Instant completedAt) { succeeded = true; }
        @Override public void markFailed(UUID jobId, String failure, Instant completedAt) {
            failed = true;
            failureReason = failure;
        }
        @Override public Optional<DltReplayJob> find(UUID jobId) { return Optional.ofNullable(created); }
    }
}

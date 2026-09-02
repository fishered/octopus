package com.accuenergy.octopus.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.accuenergy.octopus.api.telemetry.ReadingQuality;
import com.accuenergy.octopus.api.telemetry.TelemetryIngressFailure;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.control.application.port.TelemetryIngressPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class TelemetryIngressServiceTest {
    private static final Instant RECEIVED = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID tenantId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final MemoryPublisher publisher = new MemoryPublisher();

    @Test
    void publishesWithKafkaOrderingKeyAndAuthoritativeReceivedTime() throws Exception {
        TelemetryReading decoded = reading(tenantId, deviceId);
        TelemetryIngressService service = service(decoded);
        var outcome = service.ingest(bytes(), new TelemetryIngressService.UplinkMetadata(
                topic(tenantId, deviceId), 1, false)).toCompletableFuture().get();
        assertEquals(TelemetryIngressService.Outcome.RAW_PUBLISHED, outcome);
        assertEquals(RECEIVED, publisher.raw.receivedAt());
        assertEquals(decoded.orderingKey(), publisher.raw.orderingKey());
        assertNull(publisher.failure);
    }

    @Test
    void identityMismatchIsDurablyQuarantined() throws Exception {
        TelemetryIngressService service = service(reading(tenantId, UUID.randomUUID()));
        var outcome = service.ingest(bytes(), new TelemetryIngressService.UplinkMetadata(
                topic(tenantId, deviceId), 1, false)).toCompletableFuture().get();
        assertEquals(TelemetryIngressService.Outcome.QUARANTINED, outcome);
        assertNull(publisher.raw);
        assertEquals(topic(tenantId, deviceId), publisher.failure.sourceTopic());
    }

    @Test
    void qosZeroIsQuarantinedInsteadOfAcknowledgedAsRaw() throws Exception {
        TelemetryIngressService service = service(reading(tenantId, deviceId));
        var outcome = service.ingest(bytes(), new TelemetryIngressService.UplinkMetadata(
                topic(tenantId, deviceId), 0, false)).toCompletableFuture().get();
        assertEquals(TelemetryIngressService.Outcome.QUARANTINED, outcome);
    }

    private TelemetryIngressService service(TelemetryReading decoded) {
        return new TelemetryIngressService(payload -> decoded, publisher,
                new DevicePresenceService((tenant, device, at, ttl) -> { }, java.time.Duration.ofMinutes(2)),
                Clock.fixed(RECEIVED, ZoneOffset.UTC));
    }

    private TelemetryReading reading(UUID tenant, UUID device) {
        return new TelemetryReading(1, UUID.randomUUID(), tenant, device, UUID.randomUUID(),
                UUID.randomUUID(), 1, "boot-1", 1, RECEIVED.minusSeconds(1),
                RECEIVED.minusSeconds(30), BigDecimal.ONE, "kWh", ReadingQuality.GOOD);
    }

    private static byte[] bytes() { return "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static String topic(UUID tenant, UUID device) {
        return "octopus/" + tenant + "/devices/" + device + "/telemetry";
    }

    private static final class MemoryPublisher implements TelemetryIngressPublisher {
        private TelemetryReading raw;
        private TelemetryIngressFailure failure;
        public CompletionStage<Void> publishRaw(TelemetryReading reading) {
            raw = reading; return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<Void> publishQuarantine(TelemetryIngressFailure value) {
            failure = value; return CompletableFuture.completedFuture(null);
        }
    }
}

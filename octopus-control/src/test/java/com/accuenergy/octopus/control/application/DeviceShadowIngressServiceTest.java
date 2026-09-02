package com.accuenergy.octopus.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.accuenergy.octopus.api.control.DeviceShadowIngressFailure;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.control.application.port.DeviceShadowIngressPublisher;
import com.accuenergy.octopus.control.application.port.DeviceShadowPayloadDecoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DeviceShadowIngressServiceTest {
    private static final Instant RECEIVED = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID tenantId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final MemoryPublisher publisher = new MemoryPublisher();
    private Observation observation;

    @Test
    void publishesCanonicalReportAndRefreshesPresenceAfterKafka() throws Exception {
        DeviceShadowIngressService service = service(decoded(tenantId, deviceId));

        var outcome = service.ingest("{}".getBytes(), metadata(false)).toCompletableFuture().get();

        assertEquals(DeviceShadowIngressService.Outcome.REPORTED_PUBLISHED, outcome);
        assertEquals(RECEIVED, publisher.reported.receivedAt());
        assertEquals(tenantId + ":" + deviceId, publisher.reported.orderingKey());
        assertEquals(new Observation(tenantId, deviceId, RECEIVED), observation);
        assertNull(publisher.failure);
    }

    @Test
    void retainedReportUpdatesProjectionWithoutClaimingDeviceIsOnline() throws Exception {
        DeviceShadowIngressService service = service(decoded(tenantId, deviceId));

        assertEquals(DeviceShadowIngressService.Outcome.REPORTED_PUBLISHED,
                service.ingest("{}".getBytes(), metadata(true)).toCompletableFuture().get());
        assertNull(observation);
    }

    @Test
    void topicPayloadMismatchIsQuarantined() throws Exception {
        DeviceShadowIngressService service = service(decoded(tenantId, UUID.randomUUID()));

        assertEquals(DeviceShadowIngressService.Outcome.QUARANTINED,
                service.ingest("{}".getBytes(), metadata(false)).toCompletableFuture().get());
        assertNull(publisher.reported);
        assertEquals(topic(), publisher.failure.sourceTopic());
    }

    @Test
    void oversizedReportUsesBoundedQuarantineSample() throws Exception {
        DeviceShadowIngressService service = service(decoded(tenantId, deviceId));

        assertEquals(DeviceShadowIngressService.Outcome.QUARANTINED,
                service.ingest(new byte[300_000], metadata(false)).toCompletableFuture().get());
        assertEquals(65_536, publisher.failure.payload().length);
    }

    private DeviceShadowIngressService service(DeviceShadowPayloadDecoder.DecodedShadow decoded) {
        DevicePresenceService presence = new DevicePresenceService((tenant, device, at, ttl) ->
                observation = new Observation(tenant, device, at), Duration.ofMinutes(2));
        return new DeviceShadowIngressService(payload -> decoded, publisher, presence,
                Clock.fixed(RECEIVED, ZoneOffset.UTC));
    }

    private DeviceShadowPayloadDecoder.DecodedShadow decoded(UUID tenant, UUID device) {
        return new DeviceShadowPayloadDecoder.DecodedShadow(1, UUID.randomUUID(), tenant, device, 7,
                "{\"temperature\":21}", RECEIVED.minusSeconds(1));
    }

    private DeviceShadowIngressService.UplinkMetadata metadata(boolean retained) {
        return new DeviceShadowIngressService.UplinkMetadata(topic(), 1, retained);
    }

    private String topic() { return "octopus/" + tenantId + "/devices/" + deviceId + "/shadow/reported"; }
    private record Observation(UUID tenantId, UUID deviceId, Instant observedAt) { }

    private static final class MemoryPublisher implements DeviceShadowIngressPublisher {
        private DeviceShadowReported reported;
        private DeviceShadowIngressFailure failure;
        @Override public CompletionStage<Void> publishReported(DeviceShadowReported value) {
            reported = value; return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> publishQuarantine(DeviceShadowIngressFailure value) {
            failure = value; return CompletableFuture.completedFuture(null);
        }
    }
}

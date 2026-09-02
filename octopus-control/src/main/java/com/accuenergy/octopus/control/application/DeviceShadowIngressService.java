package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.api.control.DeviceShadowIngressFailure;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.control.application.port.DeviceShadowIngressPublisher;
import com.accuenergy.octopus.control.application.port.DeviceShadowPayloadDecoder;
import com.accuenergy.octopus.control.domain.shadow.DeviceShadowTopic;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeviceShadowIngressService {
    private static final Logger log = LoggerFactory.getLogger(DeviceShadowIngressService.class);
    private static final int MAX_PAYLOAD_BYTES = 262_144;
    private final DeviceShadowPayloadDecoder decoder;
    private final DeviceShadowIngressPublisher publisher;
    private final DevicePresenceService presence;
    private final Clock clock;

    public DeviceShadowIngressService(DeviceShadowPayloadDecoder decoder, DeviceShadowIngressPublisher publisher,
            DevicePresenceService presence, Clock clock) {
        this.decoder = decoder;
        this.publisher = publisher;
        this.presence = presence;
        this.clock = clock;
    }

    public CompletionStage<Outcome> ingest(byte[] payload, UplinkMetadata metadata) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(metadata, "metadata");
        Instant receivedAt = clock.instant();
        try {
            if (metadata.qos() < 1) throw new IllegalArgumentException("QoS 0 shadow reports are not accepted");
            if (payload.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Shadow report exceeds 256 KiB");
            DeviceShadowTopic topic = DeviceShadowTopic.parse(metadata.topic());
            DeviceShadowPayloadDecoder.DecodedShadow decoded = decoder.decode(payload);
            if (!topic.tenantId().equals(decoded.tenantId()) || !topic.deviceId().equals(decoded.deviceId())) {
                throw new IllegalArgumentException("MQTT topic identity does not match shadow payload identity");
            }
            DeviceShadowReported event = new DeviceShadowReported(decoded.schemaVersion(), decoded.reportId(),
                    decoded.tenantId(), decoded.deviceId(), decoded.shadowVersion(), decoded.stateJson(),
                    decoded.reportedAt(), receivedAt, decoded.appliedDesiredVersion());
            return publisher.publishReported(event).thenApply(ignored -> {
                if (!metadata.retained()) observePresence(event, receivedAt);
                return Outcome.REPORTED_PUBLISHED;
            });
        } catch (RuntimeException invalid) {
            DeviceShadowIngressFailure failure = new DeviceShadowIngressFailure(1, UUID.randomUUID(),
                    metadata.topic(), quarantineSample(payload), abbreviated(invalid), receivedAt);
            return publisher.publishQuarantine(failure).thenApply(ignored -> Outcome.QUARANTINED);
        }
    }

    private void observePresence(DeviceShadowReported event, Instant receivedAt) {
        try {
            presence.observe(event.tenantId(), event.deviceId(), receivedAt);
        } catch (RuntimeException unavailable) {
            log.warn("Unable to refresh presence after durable shadow handoff for device {}", event.deviceId(), unavailable);
        }
    }

    private static String abbreviated(RuntimeException failure) {
        String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static byte[] quarantineSample(byte[] payload) {
        return payload.length <= 65_536 ? payload : Arrays.copyOf(payload, 65_536);
    }

    public record UplinkMetadata(String topic, int qos, boolean retained) {
        public UplinkMetadata {
            if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
            if (qos < 0 || qos > 2) throw new IllegalArgumentException("qos is invalid");
        }
    }
    public enum Outcome { REPORTED_PUBLISHED, QUARANTINED }
}

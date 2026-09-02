package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.api.telemetry.TelemetryIngressFailure;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.control.application.port.TelemetryIngressPublisher;
import com.accuenergy.octopus.control.application.port.TelemetryPayloadDecoder;
import com.accuenergy.octopus.control.domain.telemetry.DeviceTelemetryTopic;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TelemetryIngressService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryIngressService.class);
    private final TelemetryPayloadDecoder decoder;
    private final TelemetryIngressPublisher publisher;
    private final DevicePresenceService presence;
    private final Clock clock;

    public TelemetryIngressService(TelemetryPayloadDecoder decoder,
                                   TelemetryIngressPublisher publisher, DevicePresenceService presence, Clock clock) {
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
            if (metadata.qos() < 1) throw new IllegalArgumentException("QoS 0 telemetry is not accepted");
            if (metadata.retained()) throw new IllegalArgumentException("Retained telemetry is not accepted");
            DeviceTelemetryTopic topic = DeviceTelemetryTopic.parse(metadata.topic());
            TelemetryReading decoded = decoder.decode(payload);
            if (!topic.tenantId().equals(decoded.tenantId()) || !topic.deviceId().equals(decoded.deviceId())) {
                throw new IllegalArgumentException("MQTT topic identity does not match payload identity");
            }
            TelemetryReading authoritative = withReceivedAt(decoded, receivedAt);
            return publisher.publishRaw(authoritative).thenApply(ignored -> {
                try {
                    presence.observe(authoritative.tenantId(), authoritative.deviceId(), receivedAt);
                } catch (RuntimeException unavailable) {
                    log.warn("Unable to refresh presence after durable telemetry handoff for device {}",
                            authoritative.deviceId(), unavailable);
                }
                return Outcome.RAW_PUBLISHED;
            });
        } catch (RuntimeException invalid) {
            TelemetryIngressFailure failure = new TelemetryIngressFailure(1, UUID.randomUUID(), metadata.topic(),
                    payload, abbreviated(invalid), receivedAt);
            return publisher.publishQuarantine(failure).thenApply(ignored -> Outcome.QUARANTINED);
        }
    }

    private static TelemetryReading withReceivedAt(TelemetryReading reading, Instant receivedAt) {
        return new TelemetryReading(reading.schemaVersion(), reading.eventId(), reading.tenantId(),
                reading.deviceId(), reading.meterId(), reading.parameterId(), reading.modelVersion(),
                reading.bootId(), reading.sequence(), reading.occurredAt(), receivedAt, reading.value(),
                reading.unitCode(), reading.quality());
    }

    private static String abbreviated(RuntimeException failure) {
        String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record UplinkMetadata(String topic, int qos, boolean retained) {
        public UplinkMetadata {
            if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
            if (qos < 0 || qos > 2) throw new IllegalArgumentException("qos is invalid");
        }
    }

    public enum Outcome { RAW_PUBLISHED, QUARANTINED }
}

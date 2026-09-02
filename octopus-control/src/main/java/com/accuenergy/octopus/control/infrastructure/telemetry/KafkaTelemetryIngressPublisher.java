package com.accuenergy.octopus.control.infrastructure.telemetry;

import com.accuenergy.octopus.api.telemetry.TelemetryIngressFailure;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.control.application.port.TelemetryIngressPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class KafkaTelemetryIngressPublisher implements TelemetryIngressPublisher {
    private final KafkaTemplate<String, byte[]> kafka;
    private final ObjectMapper objectMapper;
    private final String rawTopic;
    private final String quarantineTopic;

    public KafkaTelemetryIngressPublisher(KafkaTemplate<String, byte[]> kafka, ObjectMapper objectMapper,
            @Value("${octopus.kafka.telemetry-raw-topic:octopus.local.telemetry.raw.v1}") String rawTopic,
            @Value("${octopus.kafka.telemetry-quarantine-topic:octopus.local.telemetry.quarantine.v1}")
            String quarantineTopic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.rawTopic = rawTopic;
        this.quarantineTopic = quarantineTopic;
    }

    @Override
    public CompletionStage<Void> publishRaw(TelemetryReading reading) {
        return publish(rawTopic, reading.orderingKey(), reading);
    }

    @Override
    public CompletionStage<Void> publishQuarantine(TelemetryIngressFailure failure) {
        return publish(quarantineTopic, failure.sourceTopic(), failure);
    }

    private CompletionStage<Void> publish(String topic, String key, Object value) {
        try {
            return kafka.send(topic, key, objectMapper.writeValueAsBytes(value))
                    .thenApply(ignored -> null);
        } catch (Exception serializationFailure) {
            return CompletableFuture.failedFuture(serializationFailure);
        }
    }
}

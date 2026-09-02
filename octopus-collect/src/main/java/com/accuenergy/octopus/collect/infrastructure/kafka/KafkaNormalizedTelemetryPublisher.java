package com.accuenergy.octopus.collect.infrastructure.kafka;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.collect.application.port.NormalizedTelemetryPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public final class KafkaNormalizedTelemetryPublisher implements NormalizedTelemetryPublisher {
    private final KafkaTemplate<String, byte[]> kafka;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaNormalizedTelemetryPublisher(KafkaTemplate<String, byte[]> kafka, ObjectMapper objectMapper,
            @Value("${octopus.kafka.telemetry-normalized-topic:octopus.local.telemetry.normalized.v1}") String topic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(NormalizedTelemetry telemetry) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(telemetry);
            kafka.send(topic, telemetry.orderingKey(), payload).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing normalized telemetry", interrupted);
        } catch (JsonProcessingException | ExecutionException failure) {
            throw new IllegalStateException("Unable to publish normalized telemetry", failure);
        }
    }
}

package com.accuenergy.octopus.collect.infrastructure.kafka;

import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.collect.application.port.QuarantinePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public final class KafkaQuarantineAdapter implements QuarantinePort {
    private final KafkaTemplate<String, byte[]> kafka;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaQuarantineAdapter(KafkaTemplate<String, byte[]> kafka, ObjectMapper objectMapper,
                                  @Value("${octopus.kafka.telemetry-quarantine-topic:octopus.local.telemetry.quarantine.v1}") String topic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void quarantine(TelemetryReading reading, String reason) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of("reason", reason, "reading", reading));
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, reading.orderingKey(), payload);
            record.headers().add("octopus-event-id", reading.eventId().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            kafka.send(record).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to durably quarantine telemetry", exception);
        }
    }
}


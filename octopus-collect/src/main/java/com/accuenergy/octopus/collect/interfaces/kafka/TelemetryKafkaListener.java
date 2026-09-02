package com.accuenergy.octopus.collect.interfaces.kafka;

import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.collect.application.TelemetryProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public final class TelemetryKafkaListener {
    private final ObjectMapper objectMapper;
    private final TelemetryProcessor processor;

    public TelemetryKafkaListener(ObjectMapper objectMapper, TelemetryProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = "${octopus.kafka.telemetry-raw-topic:octopus.local.telemetry.raw.v1}",
            groupId = "${octopus.kafka.collect-group:octopus-collect-v1}")
    public void consume(byte[] payload, @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        Acknowledgment acknowledgment) throws Exception {
        TelemetryReading reading = objectMapper.readValue(payload, TelemetryReading.class);
        requireOrderingKey(key, reading.orderingKey());
        processor.process(reading);
        acknowledgment.acknowledge();
    }

    static void requireOrderingKey(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new KafkaRecordKeyMismatchException("Telemetry Kafka key does not match its ordering domain");
        }
    }
}

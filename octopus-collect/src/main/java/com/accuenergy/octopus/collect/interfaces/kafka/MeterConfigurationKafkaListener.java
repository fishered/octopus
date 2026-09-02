package com.accuenergy.octopus.collect.interfaces.kafka;

import com.accuenergy.octopus.api.catalog.MeterConfigurationChanged;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationUpdatePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public final class MeterConfigurationKafkaListener {
    private final ObjectMapper objectMapper;
    private final MeterConfigurationUpdatePort configurations;

    public MeterConfigurationKafkaListener(ObjectMapper objectMapper,
                                           MeterConfigurationUpdatePort configurations) {
        this.objectMapper = objectMapper;
        this.configurations = configurations;
    }

    @KafkaListener(topics = "${octopus.kafka.meter-configuration-topic:octopus.local.catalog.meter-configuration.v1}",
            groupId = "${octopus.kafka.catalog-group:octopus-collect-catalog-v1}")
    public void consume(byte[] payload, @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        Acknowledgment acknowledgment) throws Exception {
        MeterConfigurationChanged event = objectMapper.readValue(payload, MeterConfigurationChanged.class);
        if (!event.meterId().toString().equals(key)) {
            throw new KafkaRecordKeyMismatchException(
                    "Meter configuration Kafka key does not match its ordering domain");
        }
        configurations.apply(event);
        acknowledgment.acknowledge();
    }
}

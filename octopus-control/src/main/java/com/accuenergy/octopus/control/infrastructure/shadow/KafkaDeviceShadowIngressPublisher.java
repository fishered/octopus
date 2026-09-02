package com.accuenergy.octopus.control.infrastructure.shadow;

import com.accuenergy.octopus.api.control.DeviceShadowIngressFailure;
import com.accuenergy.octopus.api.control.DeviceShadowReported;
import com.accuenergy.octopus.control.application.port.DeviceShadowIngressPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public final class KafkaDeviceShadowIngressPublisher implements DeviceShadowIngressPublisher {
    private final KafkaTemplate<String, byte[]> kafka;
    private final ObjectMapper objectMapper;
    private final String reportedTopic;
    private final String quarantineTopic;

    public KafkaDeviceShadowIngressPublisher(KafkaTemplate<String, byte[]> kafka, ObjectMapper objectMapper,
            @Value("${octopus.kafka.shadow-reported-topic:octopus.local.shadow.reported.v1}") String reportedTopic,
            @Value("${octopus.kafka.shadow-quarantine-topic:octopus.local.shadow.quarantine.v1}")
            String quarantineTopic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.reportedTopic = reportedTopic;
        this.quarantineTopic = quarantineTopic;
    }

    @Override public CompletionStage<Void> publishReported(DeviceShadowReported reported) {
        return publish(reportedTopic, reported.orderingKey(), reported);
    }

    @Override public CompletionStage<Void> publishQuarantine(DeviceShadowIngressFailure failure) {
        return publish(quarantineTopic, failure.sourceTopic(), failure);
    }

    private CompletionStage<Void> publish(String topic, String key, Object value) {
        try {
            return kafka.send(topic, key, objectMapper.writeValueAsBytes(value)).thenApply(ignored -> null);
        } catch (Exception serializationFailure) {
            return CompletableFuture.failedFuture(serializationFailure);
        }
    }
}

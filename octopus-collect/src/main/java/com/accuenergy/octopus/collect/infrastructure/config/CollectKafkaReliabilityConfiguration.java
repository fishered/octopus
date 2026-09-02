package com.accuenergy.octopus.collect.infrastructure.config;

import com.accuenergy.octopus.collect.interfaces.kafka.KafkaRecordKeyMismatchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class CollectKafkaReliabilityConfiguration {
    @Bean
    CommonErrorHandler collectKafkaErrorHandler(KafkaTemplate<String, byte[]> kafka,
            @Value("${octopus.kafka.telemetry-raw-topic:octopus.local.telemetry.raw.v1}") String rawTopic,
            @Value("${octopus.kafka.telemetry-dlt-topic:octopus.local.telemetry.raw.dlt.v1}") String telemetryDlt,
            @Value("${octopus.kafka.catalog-dlt-topic:octopus.local.catalog.meter-configuration.dlt.v1}") String catalogDlt,
            @Value("${octopus.kafka.retry.max-attempts:8}") int maxAttempts,
            @Value("${octopus.kafka.retry.initial-interval:PT1S}") Duration initialInterval,
            @Value("${octopus.kafka.retry.max-interval:PT30S}") Duration maxInterval) {
        if (maxAttempts < 1) throw new IllegalArgumentException("Kafka max attempts must be positive");
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(
                        record.topic().equals(rawTopic) ? telemetryDlt : catalogDlt, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(30));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxAttempts - 1);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxInterval.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class,
                KafkaRecordKeyMismatchException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }
}

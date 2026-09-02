package com.accuenergy.octopus.mgmt.infrastructure.kafka;

import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayInfrastructureException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayPublisher;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;

/** Reads exact retained DLT offsets and republishes immutable key/value bytes to an allowlisted source topic. */
@Component
public final class KafkaDltReplayAdapter implements DltRecordReader, DltReplayPublisher {
    private static final String DLT_HEADER_PREFIX = "kafka_dlt-";

    private final Map<String, String> routes;
    private final Map<String, Object> consumerProperties;
    private final KafkaProducer<String, byte[]> producer;
    private final Duration readTimeout;
    private final Duration publishTimeout;

    public KafkaDltReplayAdapter(
            KafkaProperties kafkaProperties,
            @Value("${octopus.kafka.telemetry-raw-topic:octopus.local.telemetry.raw.v1}") String telemetrySource,
            @Value("${octopus.kafka.telemetry-dlt-topic:octopus.local.telemetry.raw.dlt.v1}") String telemetryDlt,
            @Value("${octopus.kafka.meter-configuration-topic:octopus.local.catalog.meter-configuration.v1}") String catalogSource,
            @Value("${octopus.kafka.catalog-dlt-topic:octopus.local.catalog.meter-configuration.dlt.v1}") String catalogDlt,
            @Value("${octopus.kafka.dlt-replay.read-timeout:PT5S}") Duration readTimeout,
            @Value("${octopus.kafka.dlt-replay.publish-timeout:PT30S}") Duration publishTimeout) {
        this.routes = Map.of(telemetryDlt, telemetrySource, catalogDlt, catalogSource);
        if (readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("DLT replay read timeout must be positive");
        }
        if (publishTimeout.isNegative() || publishTimeout.isZero()) {
            throw new IllegalArgumentException("DLT replay publish timeout must be positive");
        }
        this.readTimeout = readTimeout;
        this.publishTimeout = publishTimeout;

        this.consumerProperties = kafkaProperties.buildConsumerProperties(null);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");

        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties(null);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProperties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        this.producer = new KafkaProducer<>(producerProperties);
    }

    @Override
    public Optional<DltRecord> read(SourcePosition position) {
        String destinationTopic = routes.get(position.topic());
        if (destinationTopic == null) {
            throw new IllegalArgumentException("DLT topic is not allowlisted for replay");
        }
        TopicPartition topicPartition = new TopicPartition(position.topic(), position.partition());
        Map<String, Object> properties = new java.util.HashMap<>(consumerProperties);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "octopus-dlt-replay-reader-" + UUID.randomUUID());
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(topicPartition));
            long beginning = consumer.beginningOffsets(List.of(topicPartition), readTimeout).get(topicPartition);
            long end = consumer.endOffsets(List.of(topicPartition), readTimeout).get(topicPartition);
            if (position.offset() < beginning || position.offset() >= end) return Optional.empty();
            consumer.seek(topicPartition, position.offset());
            for (ConsumerRecord<String, byte[]> record : consumer.poll(readTimeout).records(topicPartition)) {
                if (record.offset() == position.offset()) {
                    List<RecordHeader> headers = new ArrayList<>();
                    record.headers().forEach(header -> headers.add(new RecordHeader(header.key(), header.value())));
                    return Optional.of(new DltRecord(position, destinationTopic, record.key(), record.value(),
                            Instant.ofEpochMilli(record.timestamp()), headers));
                }
            }
            return Optional.empty();
        } catch (RuntimeException failure) {
            throw new DltReplayInfrastructureException("Unable to read the requested DLT offset", failure);
        }
    }

    @Override
    public PublishResult publish(DltRecord record, UUID replayJobId, String replayRoot, int replayAttempt) {
        String expectedDestination = routes.get(record.position().topic());
        if (!record.destinationTopic().equals(expectedDestination)) {
            throw new IllegalArgumentException("Replay destination does not match the allowlisted DLT route");
        }
        RecordHeaders headers = replayHeaders(record.headers(), replayJobId, replayRoot, replayAttempt);
        ProducerRecord<String, byte[]> replay = new ProducerRecord<>(record.destinationTopic(),
                record.position().partition(), null, record.key(), record.value(), headers);
        try {
            var metadata = producer.send(replay).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return new PublishResult(metadata.partition(), metadata.offset(),
                    Instant.ofEpochMilli(metadata.timestamp()));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new DltReplayInfrastructureException("DLT replay publication was interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new DltReplayInfrastructureException("DLT replay publication was not confirmed", failure);
        }
    }

    private static boolean isReplayMetadata(String key) {
        return key.equals(DltReplayService.REPLAY_ROOT_HEADER)
                || key.equals(DltReplayService.REPLAY_ATTEMPT_HEADER)
                || key.equals(DltReplayService.REPLAY_JOB_HEADER);
    }

    static RecordHeaders replayHeaders(List<RecordHeader> sourceHeaders, UUID replayJobId,
                                       String replayRoot, int replayAttempt) {
        RecordHeaders headers = new RecordHeaders();
        sourceHeaders.stream()
                .filter(header -> !header.key().startsWith(DLT_HEADER_PREFIX))
                .filter(header -> !isReplayMetadata(header.key()))
                .forEach(header -> headers.add(header.key(), header.value()));
        headers.add(DltReplayService.REPLAY_ROOT_HEADER, replayRoot.getBytes(StandardCharsets.UTF_8));
        headers.add(DltReplayService.REPLAY_ATTEMPT_HEADER,
                Integer.toString(replayAttempt).getBytes(StandardCharsets.UTF_8));
        headers.add(DltReplayService.REPLAY_JOB_HEADER,
                replayJobId.toString().getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    @PreDestroy
    void close() {
        producer.close(Duration.ofSeconds(5));
    }
}

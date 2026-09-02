package com.accuenergy.octopus.control.infrastructure.kafka;

import com.accuenergy.octopus.control.infrastructure.persistence.command.DeviceCommandMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KafkaCommandStatusOutboxPublisher {
    private final DeviceCommandMapper mapper;
    private final KafkaTemplate<String, byte[]> kafka;
    private final Clock clock;
    private final int batchSize;

    public KafkaCommandStatusOutboxPublisher(DeviceCommandMapper mapper, KafkaTemplate<String, byte[]> kafka,
            Clock clock, @Value("${octopus.command.status-outbox-batch-size:20}") int batchSize) {
        this.mapper = mapper;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${octopus.command.status-outbox-poll-delay:1000}")
    @Transactional(transactionManager = "platformTransactionManager")
    public void publishPending() {
        if (!mapper.tryAcquireRelayLock()) return;
        for (DeviceCommandMapper.OutboxRow event : mapper.lockPending(batchSize)) {
            try {
                kafka.send(event.topic(), event.partitionKey(), event.payload().getBytes(StandardCharsets.UTF_8))
                        .get(10, TimeUnit.SECONDS);
                if (mapper.markPublished(event.id(), clock.instant()) != 1) {
                    throw new IllegalStateException("Command status outbox event disappeared");
                }
            } catch (Exception failure) {
                mapper.markFailed(event.id(), abbreviated(failure));
            }
        }
    }

    private static String abbreviated(Exception failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

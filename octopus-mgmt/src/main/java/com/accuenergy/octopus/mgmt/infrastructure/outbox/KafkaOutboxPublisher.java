package com.accuenergy.octopus.mgmt.infrastructure.outbox;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once outbox relay. Duplicates are expected and catalog projections are deterministic. */
@Component
public class KafkaOutboxPublisher {
    private final OutboxMapper outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;

    public KafkaOutboxPublisher(OutboxMapper outbox, KafkaTemplate<String, String> kafka, Clock clock,
                                @Value("${octopus.outbox.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${octopus.outbox.poll-delay:1000}")
    @Transactional(transactionManager = "platformTransactionManager")
    public void publishPending() {
        if (!outbox.tryAcquireRelayLock()) return;
        for (OutboxMapper.OutboxRow event : outbox.lockPending(batchSize)) {
            try {
                kafka.send(event.topic(), event.partitionKey(), event.payload()).get(10, TimeUnit.SECONDS);
                if (outbox.markPublished(event.id(), clock.instant()) != 1) {
                    throw new IllegalStateException("Outbox event disappeared while publishing");
                }
            } catch (Exception failure) {
                outbox.markFailed(event.id(), abbreviated(failure));
            }
        }
    }

    private static String abbreviated(Exception failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

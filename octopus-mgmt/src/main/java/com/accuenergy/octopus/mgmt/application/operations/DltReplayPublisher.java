package com.accuenergy.octopus.mgmt.application.operations;

import java.time.Instant;
import java.util.UUID;

public interface DltReplayPublisher {
    PublishResult publish(DltRecordReader.DltRecord record, UUID replayJobId,
                          String replayRoot, int replayAttempt);

    record PublishResult(int partition, long offset, Instant timestamp) { }
}

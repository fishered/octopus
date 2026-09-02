package com.accuenergy.octopus.mgmt.application.operations;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface DltRecordReader {
    Optional<DltRecord> read(SourcePosition position);

    record SourcePosition(String topic, int partition, long offset) {
        public SourcePosition {
            Objects.requireNonNull(topic, "topic");
            topic = topic.strip();
            if (topic.isEmpty() || topic.length() > 255) {
                throw new IllegalArgumentException("DLT topic must contain 1 to 255 characters");
            }
            if (partition < 0) throw new IllegalArgumentException("DLT partition must be non-negative");
            if (offset < 0) throw new IllegalArgumentException("DLT offset must be non-negative");
        }

        public String externalForm() {
            return topic + ":" + partition + ":" + offset;
        }
    }

    record DltRecord(SourcePosition position, String destinationTopic, String key, byte[] value,
                     Instant timestamp, List<RecordHeader> headers) {
        public DltRecord {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(destinationTopic, "destinationTopic");
            destinationTopic = destinationTopic.strip();
            if (destinationTopic.isEmpty()) throw new IllegalArgumentException("destinationTopic is required");
            value = value == null ? null : Arrays.copyOf(value, value.length);
            Objects.requireNonNull(timestamp, "timestamp");
            headers = List.copyOf(headers);
        }

        @Override public byte[] value() { return value == null ? null : Arrays.copyOf(value, value.length); }
    }

    record RecordHeader(String key, byte[] value) {
        public RecordHeader {
            Objects.requireNonNull(key, "key");
            value = value == null ? null : Arrays.copyOf(value, value.length);
        }

        @Override public byte[] value() { return value == null ? null : Arrays.copyOf(value, value.length); }
    }
}

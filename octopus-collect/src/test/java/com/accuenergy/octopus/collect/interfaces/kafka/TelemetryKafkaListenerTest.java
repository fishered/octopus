package com.accuenergy.octopus.collect.interfaces.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TelemetryKafkaListenerTest {
    @Test
    void rejectsRecordsWhoseKafkaKeyDoesNotMatchOrderingDomain() {
        assertDoesNotThrow(() -> TelemetryKafkaListener.requireOrderingKey(
                "tenant:device:meter", "tenant:device:meter"));
        assertThrows(KafkaRecordKeyMismatchException.class,
                () -> TelemetryKafkaListener.requireOrderingKey("wrong", "tenant:device:meter"));
    }
}

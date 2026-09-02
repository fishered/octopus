package com.accuenergy.octopus.collect.interfaces.kafka;

public final class KafkaRecordKeyMismatchException extends RuntimeException {
    public KafkaRecordKeyMismatchException(String message) {
        super(message);
    }
}

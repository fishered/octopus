package com.accuenergy.octopus.collect.domain.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CumulativeAccumulatorTest {
    private final CumulativeAccumulator accumulator = new CumulativeAccumulator();
    private final Instant time = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void calculatesNormalDeltaWithoutFloatingPointLoss() {
        var result = accumulator.calculate(Optional.of(reading(10, "100.10")), reading(11, "101.35"), Optional.empty());
        assertEquals(new BigDecimal("1.25"), result.delta());
        assertEquals(Set.of(), result.flags());
    }

    @Test
    void reportsGapAndStillCalculatesDelta() {
        var result = accumulator.calculate(Optional.of(reading(10, "100")), reading(13, "105"), Optional.empty());
        assertEquals(new BigDecimal("5"), result.delta());
        assertEquals(Set.of(AccumulationResult.Flag.GAP), result.flags());
    }

    @Test
    void distinguishesRolloverFromResetWhenMaximumIsKnown() {
        var result = accumulator.calculate(Optional.of(reading(10, "9998")), reading(11, "3"), Optional.of(new BigDecimal("10000")));
        assertEquals(new BigDecimal("5"), result.delta());
        assertEquals(Set.of(AccumulationResult.Flag.ROLLOVER), result.flags());
    }

    @Test
    void doesNotCreateNegativeConsumptionOnReset() {
        var result = accumulator.calculate(Optional.of(reading(10, "100")), reading(11, "2"), Optional.empty());
        assertEquals(BigDecimal.ZERO, result.delta());
        assertEquals(Set.of(AccumulationResult.Flag.RESET), result.flags());
    }

    @Test
    void preservesGapFlagWhenAResetAlsoOccurs() {
        var result = accumulator.calculate(Optional.of(reading(10, "100")), reading(13, "2"), Optional.empty());
        assertEquals(Set.of(AccumulationResult.Flag.GAP, AccumulationResult.Flag.RESET), result.flags());
    }

    @Test
    void distinguishesOutOfOrderFromDuplicateSequence() {
        var outOfOrder = accumulator.calculate(Optional.of(reading(10, "100")),
                reading(9, "99"), Optional.empty());
        var duplicate = accumulator.calculate(Optional.of(reading(10, "100")),
                reading(10, "100"), Optional.empty());
        assertEquals(Set.of(AccumulationResult.Flag.OUT_OF_ORDER), outOfOrder.flags());
        assertEquals(Set.of(AccumulationResult.Flag.DUPLICATE), duplicate.flags());
    }

    @Test
    void newBootStartsANewSequenceDomainWhilePreservingCumulativeDelta() {
        var previous = new CumulativeReading("boot-1", UUID.randomUUID(), 100,
                time, new BigDecimal("100"));
        var current = new CumulativeReading("boot-2", UUID.randomUUID(), 0,
                time.plusSeconds(1), new BigDecimal("103"));

        var result = accumulator.calculate(Optional.of(previous), current, Optional.empty());

        assertEquals(new BigDecimal("3"), result.delta());
        assertEquals(Set.of(AccumulationResult.Flag.BOOT_CHANGED), result.flags());
    }

    private CumulativeReading reading(long sequence, String value) {
        return new CumulativeReading("boot-1", UUID.nameUUIDFromBytes((sequence + ":" + value).getBytes()),
                sequence, time.plusSeconds(sequence), new BigDecimal(value));
    }
}

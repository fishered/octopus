package com.accuenergy.octopus.collect.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.api.telemetry.ReadingQuality;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationPort;
import com.accuenergy.octopus.collect.application.port.MeterStatePort;
import com.accuenergy.octopus.collect.application.port.NormalizedTelemetryPublisher;
import com.accuenergy.octopus.collect.application.port.QuarantinePort;
import com.accuenergy.octopus.collect.application.port.TelemetrySinkPort;
import com.accuenergy.octopus.collect.domain.meter.CumulativeAccumulator;
import com.accuenergy.octopus.collect.domain.meter.CumulativeReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelemetryProcessorTest {
    @Test
    void writesThenMarksStateAndTreatsRetryAsDuplicate() {
        UUID parameterId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var config = new MeterConfigurationPort.MeterConfiguration(parameterId,
                MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE,
                "Wh", "kWh", new BigDecimal("0.001"), BigDecimal.ZERO, Optional.empty(), 0, 1);
        var state = new MemoryState();
        var sink = new MemorySink();
        var publisher = new MemoryPublisher();
        var processor = new TelemetryProcessor((tenant, meter, version) -> Optional.of(config), state,
                sink, publisher, (reading, reason) -> { throw new AssertionError(reason); }, new CumulativeAccumulator());
        var reading = reading(meterId, parameterId, "1000");

        assertEquals(TelemetryProcessingResult.WRITTEN, processor.process(reading));
        assertEquals(TelemetryProcessingResult.DUPLICATE, processor.process(reading));
        assertEquals(1, sink.writes);
        assertEquals(1, publisher.publishes);
        assertEquals(new BigDecimal("1.000"), sink.last.rawValue());
    }

    @Test
    void writesOutOfOrderRawValueWithoutRollingStateBack() {
        UUID parameterId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var config = new MeterConfigurationPort.MeterConfiguration(parameterId,
                MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE,
                "Wh", "kWh", new BigDecimal("0.001"), BigDecimal.ZERO, Optional.empty(), 0, 1);
        var state = new MemoryState();
        state.previous = new CumulativeReading("boot-1", UUID.randomUUID(), 10,
                Instant.parse("2026-01-01T00:00:10Z"), new BigDecimal("2"));
        var sink = new MemorySink();
        var publisher = new MemoryPublisher();
        var processor = new TelemetryProcessor((tenant, meter, version) -> Optional.of(config), state,
                sink, publisher, (reading, reason) -> { throw new AssertionError(reason); }, new CumulativeAccumulator());
        TelemetryReading late = new TelemetryReading(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                meterId, parameterId, 1, "boot-1", 9, Instant.parse("2026-01-01T00:00:09Z"),
                Instant.parse("2026-01-01T00:00:20Z"), new BigDecimal("1500"), "Wh", ReadingQuality.LATE);

        assertEquals(TelemetryProcessingResult.WRITTEN, processor.process(late));
        assertEquals(10, state.previous.sequence());
        assertEquals(Set.of(ReadingQuality.LATE, ReadingQuality.OUT_OF_ORDER), sink.last.quality());
    }

    @Test
    void doesNotAdvanceStateWhenNormalizedPublicationFails() {
        UUID parameterId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var config = new MeterConfigurationPort.MeterConfiguration(parameterId,
                MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE,
                "Wh", "kWh", new BigDecimal("0.001"), BigDecimal.ZERO, Optional.empty(), 0, 1);
        var state = new MemoryState();
        var processor = new TelemetryProcessor((tenant, meter, version) -> Optional.of(config), state,
                telemetry -> { }, telemetry -> { throw new IllegalStateException("Kafka unavailable"); },
                (reading, reason) -> { throw new AssertionError(reason); }, new CumulativeAccumulator());
        TelemetryReading reading = reading(meterId, parameterId, "1000");

        assertThrows(IllegalStateException.class, () -> processor.process(reading));
        assertEquals(false, state.wasProcessed(reading.tenantId(), reading.meterId(), reading.eventId()));
    }

    @Test
    void acceptsNewBootThenKeepsLateRetiredBootFromRollingStateBack() {
        UUID parameterId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var config = new MeterConfigurationPort.MeterConfiguration(parameterId,
                MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE,
                "Wh", "Wh", BigDecimal.ONE, BigDecimal.ZERO, Optional.empty(), 0, 1);
        var state = new MemoryState();
        state.previous = new CumulativeReading("boot-1", UUID.randomUUID(), 99,
                Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("100"));
        var sink = new MemorySink();
        var processor = new TelemetryProcessor((tenant, meter, version) -> Optional.of(config), state,
                sink, telemetry -> { }, (reading, reason) -> { throw new AssertionError(reason); },
                new CumulativeAccumulator());
        TelemetryReading newBoot = reading(meterId, parameterId, "103", "boot-2", 0,
                Instant.parse("2026-01-01T00:00:01Z"));

        assertEquals(TelemetryProcessingResult.WRITTEN, processor.process(newBoot));
        assertEquals("boot-2", state.previous.bootId());
        assertEquals(Set.of(ReadingQuality.GOOD, ReadingQuality.BOOT_CHANGED), sink.last.quality());
        assertEquals(new BigDecimal("3"), sink.last.delta());

        TelemetryReading lateOldBoot = reading(meterId, parameterId, "101", "boot-1", 100,
                Instant.parse("2026-01-01T00:00:00.500Z"));
        assertEquals(TelemetryProcessingResult.WRITTEN, processor.process(lateOldBoot));
        assertEquals("boot-2", state.previous.bootId());
        assertTrue(sink.last.quality().contains(ReadingQuality.OUT_OF_ORDER));
    }

    @Test
    void sameSequenceWithDifferentEventIdIsQuarantinedInsteadOfSilentlyDropped() {
        UUID parameterId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        var config = new MeterConfigurationPort.MeterConfiguration(parameterId,
                MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE,
                "Wh", "Wh", BigDecimal.ONE, BigDecimal.ZERO, Optional.empty(), 0, 1);
        var state = new MemoryState();
        state.previous = new CumulativeReading("boot-1", UUID.randomUUID(), 1,
                Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("100"));
        String[] reason = new String[1];
        var processor = new TelemetryProcessor((tenant, meter, version) -> Optional.of(config), state,
                telemetry -> { throw new AssertionError("must not write"); },
                telemetry -> { throw new AssertionError("must not publish"); },
                (reading, value) -> reason[0] = value, new CumulativeAccumulator());
        TelemetryReading conflict = reading(meterId, parameterId, "999", "boot-1", 1,
                Instant.parse("2026-01-01T00:00:01Z"));

        assertEquals(TelemetryProcessingResult.QUARANTINED, processor.process(conflict));
        assertEquals("Sequence is already assigned to a different event", reason[0]);
        assertTrue(state.wasProcessed(conflict.tenantId(), meterId, conflict.eventId()));
    }

    private static TelemetryReading reading(UUID meterId, UUID parameterId, String value) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return reading(meterId, parameterId, value, "boot-1", 1, now);
    }

    private static TelemetryReading reading(UUID meterId, UUID parameterId, String value,
                                            String bootId, long sequence, Instant occurredAt) {
        return new TelemetryReading(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), meterId,
                parameterId, 1, bootId, sequence, occurredAt, occurredAt.plusSeconds(1),
                new BigDecimal(value), "Wh", ReadingQuality.GOOD);
    }

    private static final class MemoryState implements MeterStatePort {
        private CumulativeReading previous;
        private final Set<UUID> events = new HashSet<>();
        private final Set<String> retiredBoots = new HashSet<>();
        public Optional<CumulativeReading> previous(UUID tenantId, UUID meterId) { return Optional.ofNullable(previous); }
        public void save(UUID tenantId, UUID meterId, CumulativeReading reading) {
            if (previous != null && !previous.bootId().equals(reading.bootId())) retiredBoots.add(previous.bootId());
            previous = reading; events.add(reading.eventId());
        }
        public void markProcessed(UUID tenantId, UUID meterId, UUID eventId) { events.add(eventId); }
        public boolean wasProcessed(UUID tenantId, UUID meterId, UUID eventId) { return events.contains(eventId); }
        public boolean isRetiredBoot(UUID tenantId, UUID meterId, String bootId) { return retiredBoots.contains(bootId); }
    }

    private static final class MemorySink implements TelemetrySinkPort {
        private int writes;
        private NormalizedTelemetry last;
        public void write(NormalizedTelemetry telemetry) { writes++; last = telemetry; }
    }

    private static final class MemoryPublisher implements NormalizedTelemetryPublisher {
        private int publishes;
        public void publish(NormalizedTelemetry telemetry) { publishes++; }
    }
}

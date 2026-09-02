package com.accuenergy.octopus.collect.application;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;
import com.accuenergy.octopus.api.telemetry.ReadingQuality;
import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.collect.application.port.MeterConfigurationPort;
import com.accuenergy.octopus.collect.application.port.MeterStatePort;
import com.accuenergy.octopus.collect.application.port.NormalizedTelemetryPublisher;
import com.accuenergy.octopus.collect.application.port.QuarantinePort;
import com.accuenergy.octopus.collect.application.port.TelemetrySinkPort;
import com.accuenergy.octopus.collect.domain.meter.AccumulationResult;
import com.accuenergy.octopus.collect.domain.meter.CumulativeAccumulator;
import com.accuenergy.octopus.collect.domain.meter.CumulativeReading;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;

public final class TelemetryProcessor {
    private final MeterConfigurationPort configurations;
    private final MeterStatePort state;
    private final TelemetrySinkPort sink;
    private final NormalizedTelemetryPublisher publisher;
    private final QuarantinePort quarantine;
    private final CumulativeAccumulator accumulator;

    public TelemetryProcessor(MeterConfigurationPort configurations, MeterStatePort state,
                              TelemetrySinkPort sink, NormalizedTelemetryPublisher publisher,
                              QuarantinePort quarantine,
                              CumulativeAccumulator accumulator) {
        this.configurations = configurations;
        this.state = state;
        this.sink = sink;
        this.publisher = publisher;
        this.quarantine = quarantine;
        this.accumulator = accumulator;
    }

    public TelemetryProcessingResult process(TelemetryReading reading) {
        if (state.wasProcessed(reading.tenantId(), reading.meterId(), reading.eventId())) {
            return TelemetryProcessingResult.DUPLICATE;
        }
        var configuration = configurations.find(reading.tenantId(), reading.meterId(), reading.modelVersion());
        if (configuration.isEmpty()) {
            quarantine.quarantine(reading, "Unknown meter or model version");
            return TelemetryProcessingResult.QUARANTINED;
        }
        var config = configuration.orElseThrow();
        if (!config.parameterId().equals(reading.parameterId()) || !config.sourceUnitCode().equals(reading.unitCode())) {
            quarantine.quarantine(reading, "Parameter or source unit does not match the published model");
            return TelemetryProcessingResult.QUARANTINED;
        }

        BigDecimal canonicalValue = reading.value().multiply(config.scale()).add(config.offset());
        CumulativeReading current = new CumulativeReading(reading.bootId(), reading.eventId(),
                reading.sequence(), reading.occurredAt(), canonicalValue);
        Optional<CumulativeReading> previous = state.previous(reading.tenantId(), reading.meterId());
        AccumulationResult accumulation = calculate(config, current, reading, previous);
        if (accumulation.flags().contains(AccumulationResult.Flag.DUPLICATE)) {
            if (previous.isPresent() && previous.orElseThrow().eventId().equals(reading.eventId())) {
                state.markProcessed(reading.tenantId(), reading.meterId(), reading.eventId());
                return TelemetryProcessingResult.DUPLICATE;
            }
            quarantine.quarantine(reading, "Sequence is already assigned to a different event");
            state.markProcessed(reading.tenantId(), reading.meterId(), reading.eventId());
            return TelemetryProcessingResult.QUARANTINED;
        }
        EnumSet<ReadingQuality> quality = EnumSet.of(reading.quality());
        accumulation.flags().forEach(flag -> quality.add(toQuality(flag)));

        BigDecimal delta = config.semantics() == MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE
                ? accumulation.delta() : BigDecimal.ZERO;
        BigDecimal interval = config.semantics() == MeterConfigurationPort.MeterConfiguration.ValueSemantics.INTERVAL_DELTA
                ? canonicalValue : delta;
        var normalized = new NormalizedTelemetry(
                2, reading.eventId(), reading.tenantId(), reading.deviceId(), reading.meterId(), reading.parameterId(),
                reading.modelVersion(), config.algorithmVersion(), reading.bootId(), reading.sequence(), reading.occurredAt(),
                reading.receivedAt(), canonicalValue, delta, interval, config.canonicalUnitCode(), quality);

        // Deterministic Influx series/timestamp makes a retry an idempotent overwrite.
        sink.write(normalized);
        // Publish before advancing state. A retry may republish the same sourceEventId; consumers must be idempotent.
        publisher.publish(normalized);
        if (accumulation.flags().contains(AccumulationResult.Flag.OUT_OF_ORDER)) {
            state.markProcessed(reading.tenantId(), reading.meterId(), reading.eventId());
        } else {
            state.save(reading.tenantId(), reading.meterId(), current);
        }
        return TelemetryProcessingResult.WRITTEN;
    }

    private AccumulationResult calculate(MeterConfigurationPort.MeterConfiguration config,
                                         CumulativeReading current, TelemetryReading reading,
                                         Optional<CumulativeReading> previous) {
        if (previous.isPresent() && !previous.orElseThrow().bootId().equals(current.bootId())
                && state.isRetiredBoot(reading.tenantId(), reading.meterId(), current.bootId())) {
            return new AccumulationResult(BigDecimal.ZERO,
                    java.util.Set.of(AccumulationResult.Flag.OUT_OF_ORDER));
        }
        if (config.semantics() != MeterConfigurationPort.MeterConfiguration.ValueSemantics.CUMULATIVE) {
            return new AccumulationResult(BigDecimal.ZERO, accumulator.orderingFlags(previous, current));
        }
        return accumulator.calculate(previous, current, config.rolloverModulus());
    }

    private static ReadingQuality toQuality(AccumulationResult.Flag flag) {
        return switch (flag) {
            case INITIAL -> ReadingQuality.GOOD;
            case BOOT_CHANGED -> ReadingQuality.BOOT_CHANGED;
            case DUPLICATE -> ReadingQuality.INVALID;
            case OUT_OF_ORDER -> ReadingQuality.OUT_OF_ORDER;
            case GAP -> ReadingQuality.GAP_DETECTED;
            case RESET -> ReadingQuality.RESET_DETECTED;
            case ROLLOVER -> ReadingQuality.ROLLOVER_DETECTED;
        };
    }
}

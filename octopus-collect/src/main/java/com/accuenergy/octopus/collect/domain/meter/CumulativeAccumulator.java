package com.accuenergy.octopus.collect.domain.meter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;

/** Stateless calculation; callers persist the previous reading and algorithm version. */
public final class CumulativeAccumulator {
    public AccumulationResult calculate(
            Optional<CumulativeReading> previous,
            CumulativeReading current,
            Optional<BigDecimal> rolloverModulus) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(rolloverModulus, "rolloverModulus");
        Set<AccumulationResult.Flag> ordering = orderingFlags(previous, current);
        if (ordering.contains(AccumulationResult.Flag.INITIAL)
                || ordering.contains(AccumulationResult.Flag.DUPLICATE)
                || ordering.contains(AccumulationResult.Flag.OUT_OF_ORDER)) {
            return new AccumulationResult(BigDecimal.ZERO, ordering);
        }
        CumulativeReading old = previous.orElseThrow();
        EnumSet<AccumulationResult.Flag> flags = EnumSet.noneOf(AccumulationResult.Flag.class);
        flags.addAll(ordering);
        BigDecimal directDelta = current.value().subtract(old.value());
        if (directDelta.signum() >= 0) {
            return new AccumulationResult(directDelta, flags);
        }

        if (rolloverModulus.isPresent() && old.value().compareTo(rolloverModulus.orElseThrow()) < 0) {
            BigDecimal delta = rolloverModulus.orElseThrow().subtract(old.value()).add(current.value());
            if (delta.signum() >= 0) {
                flags.add(AccumulationResult.Flag.ROLLOVER);
                return new AccumulationResult(delta, flags);
            }
        }
        flags.add(AccumulationResult.Flag.RESET);
        return new AccumulationResult(BigDecimal.ZERO, flags);
    }

    public Set<AccumulationResult.Flag> orderingFlags(Optional<CumulativeReading> previous,
                                                       CumulativeReading current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (previous.isEmpty()) return Set.of(AccumulationResult.Flag.INITIAL);
        CumulativeReading old = previous.orElseThrow();
        if (!current.bootId().equals(old.bootId())) {
            return Set.of(AccumulationResult.Flag.BOOT_CHANGED);
        }
        if (current.sequence() == old.sequence()) {
            return Set.of(AccumulationResult.Flag.DUPLICATE);
        }
        if (current.sequence() < old.sequence()) {
            return Set.of(AccumulationResult.Flag.OUT_OF_ORDER);
        }
        if (current.sequence() != old.sequence() + 1) {
            return Set.of(AccumulationResult.Flag.GAP);
        }
        return Set.of();
    }
}

package com.accuenergy.octopus.collect.domain.meter;

import java.math.BigDecimal;
import java.util.Set;

public record AccumulationResult(BigDecimal delta, Set<Flag> flags) {
    public AccumulationResult {
        flags = Set.copyOf(flags);
    }

    public enum Flag { INITIAL, BOOT_CHANGED, DUPLICATE, OUT_OF_ORDER, GAP, RESET, ROLLOVER }
}

package com.accuenergy.octopus.collect.application.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface MeterConfigurationPort {
    Optional<MeterConfiguration> find(UUID tenantId, UUID meterId, long modelVersion);

    record MeterConfiguration(UUID parameterId, ValueSemantics semantics, String sourceUnitCode,
                              String canonicalUnitCode, BigDecimal scale, BigDecimal offset,
                              Optional<BigDecimal> rolloverModulus, long configurationVersion,
                              long algorithmVersion) {
        public enum ValueSemantics { INSTANTANEOUS, CUMULATIVE, INTERVAL_DELTA, NET }
    }
}

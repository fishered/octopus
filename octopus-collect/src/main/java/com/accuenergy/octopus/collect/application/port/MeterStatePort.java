package com.accuenergy.octopus.collect.application.port;

import com.accuenergy.octopus.collect.domain.meter.CumulativeReading;
import java.util.Optional;
import java.util.UUID;

public interface MeterStatePort {
    Optional<CumulativeReading> previous(UUID tenantId, UUID meterId);
    void save(UUID tenantId, UUID meterId, CumulativeReading reading);
    void markProcessed(UUID tenantId, UUID meterId, UUID eventId);
    boolean wasProcessed(UUID tenantId, UUID meterId, UUID eventId);
    boolean isRetiredBoot(UUID tenantId, UUID meterId, String bootId);
}

package com.accuenergy.octopus.iot.spi;

import java.util.Optional;
import java.util.UUID;

/** Reads the versioned management projection used for per-device plugin routing. */
@FunctionalInterface
public interface DeviceBindingResolver {
    Optional<DeviceBinding> find(UUID tenantId, UUID deviceId);
}

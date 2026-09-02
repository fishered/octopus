package com.accuenergy.octopus.control.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface DevicePresenceLeaseWriter {
    void observe(UUID tenantId, UUID deviceId, Instant observedAt, Duration leaseDuration);
}

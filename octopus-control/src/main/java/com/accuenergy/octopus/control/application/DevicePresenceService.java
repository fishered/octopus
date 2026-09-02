package com.accuenergy.octopus.control.application;

import com.accuenergy.octopus.control.application.port.DevicePresenceLeaseWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DevicePresenceService {
    private final DevicePresenceLeaseWriter leases;
    private final Duration leaseDuration;

    public DevicePresenceService(DevicePresenceLeaseWriter leases, Duration leaseDuration) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isZero() || leaseDuration.isNegative() || leaseDuration.toMillis() < 1) {
            throw new IllegalArgumentException("leaseDuration must be at least 1 ms");
        }
    }

    public void observe(UUID tenantId, UUID deviceId, Instant observedAt) {
        leases.observe(Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(deviceId, "deviceId"), Objects.requireNonNull(observedAt, "observedAt"),
                leaseDuration);
    }
}

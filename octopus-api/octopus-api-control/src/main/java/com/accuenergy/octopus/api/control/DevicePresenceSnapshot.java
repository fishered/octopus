package com.accuenergy.octopus.api.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral monitoring view. ONLINE is backed by a renewable Redis lease. */
public record DevicePresenceSnapshot(UUID tenantId, UUID deviceId, Status status,
        Instant lastSeenAt, Instant leaseExpiresAt) {
    public DevicePresenceSnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(status, "status");
        if (status == Status.NEVER_SEEN) {
            if (lastSeenAt != null || leaseExpiresAt != null) {
                throw new IllegalArgumentException("NEVER_SEEN cannot contain observation times");
            }
        } else if (lastSeenAt == null) {
            throw new IllegalArgumentException("Observed presence requires lastSeenAt");
        }
        if (status == Status.ONLINE) {
            if (leaseExpiresAt == null || !leaseExpiresAt.isAfter(lastSeenAt)) {
                throw new IllegalArgumentException("ONLINE requires an active lease");
            }
        } else if (leaseExpiresAt != null) {
            throw new IllegalArgumentException("Only ONLINE may expose leaseExpiresAt");
        }
    }

    public enum Status { NEVER_SEEN, ONLINE, OFFLINE, DEGRADED }
}

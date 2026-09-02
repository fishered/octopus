package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record SessionSnapshot(UUID sessionId, UUID accountId, Optional<UUID> tenantId,
                              long refreshGeneration, long accountGeneration,
                              long authorizationGeneration, Status status,
                              Instant idleExpiresAt, Instant absoluteExpiresAt) {
    public SessionSnapshot {
        tenantId = Optional.ofNullable(tenantId).orElseGet(Optional::empty);
    }

    public boolean isUsableAt(Instant now) {
        return status == Status.ACTIVE && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }

    public enum Status { ACTIVE, REVOKED, EXPIRED }
}

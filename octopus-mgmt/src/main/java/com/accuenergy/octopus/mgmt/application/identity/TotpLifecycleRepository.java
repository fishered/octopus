package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TotpLifecycleRepository {
    Optional<Factor> findActive(UUID accountId);
    Optional<Factor> findPending(UUID accountId, UUID factorId, Instant now);
    Optional<Factor> findCurrent(UUID accountId);

    UUID createPending(UUID accountId, String encryptedSecret, String keyVersion,
                       UUID actorAccountId, UUID sessionId, Instant expiresAt, Instant now);

    void activate(Factor factor, long acceptedCounter, List<String> recoveryCodeHashes,
                  long sessionGeneration, UUID actorAccountId, UUID sessionId, Instant now);

    void revoke(UUID accountId, long sessionGeneration, UUID actorAccountId, UUID sessionId,
                String eventType, String reason, Instant now);

    record Factor(UUID factorId, UUID accountId, String encryptedSecret,
                  Status status, Optional<Instant> expiresAt) {
        public Factor {
            expiresAt = java.util.Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    enum Status { PENDING, ACTIVE }
}

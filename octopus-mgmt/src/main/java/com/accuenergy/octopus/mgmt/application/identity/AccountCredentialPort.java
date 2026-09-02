package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface AccountCredentialPort {
    Optional<AccountCredential> findByLogin(String normalizedLogin);
    Optional<AccountCredential> findById(UUID accountId);
    void recordFailure(String normalizedLogin);
    void clearFailures(String normalizedLogin);
    int advanceSessionGeneration(UUID accountId, long generation, Instant now);
}

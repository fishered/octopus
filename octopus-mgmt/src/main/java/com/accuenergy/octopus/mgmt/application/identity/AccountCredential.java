package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AccountCredential(UUID accountId, String passwordHash, long sessionGeneration,
                                Status status, Optional<TotpFactor> totpFactor) {
    public AccountCredential {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(status, "status");
        totpFactor = Objects.requireNonNull(totpFactor, "totpFactor");
    }
    public record TotpFactor(UUID factorId, String encryptedSecret) {
        public TotpFactor {
            Objects.requireNonNull(factorId, "factorId");
            if (encryptedSecret == null || encryptedSecret.isBlank()) {
                throw new IllegalArgumentException("encryptedSecret is required");
            }
        }
    }
    public enum Status { ACTIVE, LOCKED, SUSPENDED, DISABLED }
}

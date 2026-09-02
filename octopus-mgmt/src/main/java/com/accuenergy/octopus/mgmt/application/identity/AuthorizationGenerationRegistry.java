package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Optional;
import java.util.UUID;

/** Authoritative hot-path generation used to invalidate JWTs after role/scope changes. */
public interface AuthorizationGenerationRegistry {
    long current(UUID accountId, Optional<UUID> tenantId, long persistentBaseline);
    long revoke(UUID accountId, Optional<UUID> tenantId, long persistentBaseline);
}

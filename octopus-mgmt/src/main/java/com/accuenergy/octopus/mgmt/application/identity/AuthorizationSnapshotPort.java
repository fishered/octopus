package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationSnapshotPort {
    Optional<AuthorizationSnapshot> resolve(UUID accountId, Optional<UUID> requestedTenantId);
}


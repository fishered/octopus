package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MutableSessionRegistry extends SessionRegistry {
    SessionSnapshot create(UUID accountId, Optional<UUID> tenantId, long accountSessionGeneration,
                           long authorizationGeneration, Instant now, Duration idleTimeout, Duration absoluteTimeout);
    SessionSnapshot rotate(UUID sessionId, long expectedRefreshGeneration, long authorizationGeneration,
                           Instant now, Duration idleTimeout);
}

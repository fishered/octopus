package com.accuenergy.octopus.mgmt.application.identity;

import java.util.Optional;
import java.util.UUID;

public interface SessionRegistry {
    Optional<SessionSnapshot> find(UUID sessionId);
    void revoke(UUID sessionId, String reason);
}


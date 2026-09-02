package com.accuenergy.octopus.mgmt.application.identity;

import java.util.UUID;

public interface RefreshTokenStore extends RefreshTokenVerifier {
    void replace(UUID sessionId, long refreshGeneration, String rawToken);
}


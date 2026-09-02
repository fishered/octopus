package com.accuenergy.octopus.mgmt.application.identity;

import java.util.UUID;

public interface RefreshTokenVerifier {
    boolean matches(UUID sessionId, long refreshGeneration, String presentedToken);
}


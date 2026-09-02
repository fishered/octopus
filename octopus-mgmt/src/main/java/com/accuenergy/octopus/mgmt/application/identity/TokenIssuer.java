package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.UUID;

public interface TokenIssuer {
    TokenPair issue(SessionSnapshot session, AuthorizationSnapshot authorization, Instant now);
    TokenPair rotate(SessionSnapshot session, AuthorizationSnapshot authorization, Instant now);

    record TokenPair(String accessToken, String refreshToken, UUID sessionId, long refreshGeneration,
                     Instant accessExpiresAt, Instant sessionExpiresAt) { }
}

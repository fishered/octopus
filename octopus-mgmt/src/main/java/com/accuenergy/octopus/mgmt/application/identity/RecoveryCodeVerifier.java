package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.UUID;

public interface RecoveryCodeVerifier {
    boolean consume(UUID accountId, UUID factorId, String recoveryCode, Instant now);
}

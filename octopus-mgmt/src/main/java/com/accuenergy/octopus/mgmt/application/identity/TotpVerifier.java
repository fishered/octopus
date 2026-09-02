package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.UUID;

public interface TotpVerifier {
    boolean verify(UUID factorId, String encryptedSecret, String code, Instant now);
}

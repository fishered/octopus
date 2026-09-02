package com.accuenergy.octopus.mgmt.application.identity;

import java.time.Instant;
import java.util.OptionalLong;

public interface TotpEnrollmentVerifier {
    OptionalLong verifyCandidate(String encryptedSecret, String code, Instant now);
}

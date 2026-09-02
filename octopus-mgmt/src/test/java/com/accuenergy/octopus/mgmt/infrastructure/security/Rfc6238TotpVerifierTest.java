package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class Rfc6238TotpVerifierTest {
    @Test
    void matchesRfc6238Sha1Vector() {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        AtomicLong last = new AtomicLong(-1);
        TotpCounterStore counters = (factor, counter) -> {
            long previous = last.get();
            return counter > previous && last.compareAndSet(previous, counter);
        };
        UUID factorId = UUID.randomUUID();
        var verifier = new Rfc6238TotpVerifier(value -> secret.clone(), counters, 8, 30, 0, "HmacSHA1");
        assertTrue(verifier.verify(factorId, "encrypted", "94287082", Instant.ofEpochSecond(59)));
        assertFalse(verifier.verify(factorId, "encrypted", "94287082", Instant.ofEpochSecond(59)));
        assertFalse(verifier.verify(factorId, "encrypted", "94287081", Instant.ofEpochSecond(59)));
    }

    @Test
    void rejectsMalformedCodeWithoutDecryptingSecret() {
        var verifier = new Rfc6238TotpVerifier(value -> { throw new AssertionError("must not decrypt"); },
                (factor, counter) -> true);
        assertFalse(verifier.verify(UUID.randomUUID(), "encrypted", "12ab56", Instant.EPOCH));
    }
}

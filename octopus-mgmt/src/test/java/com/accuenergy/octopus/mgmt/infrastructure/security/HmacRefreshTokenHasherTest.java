package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class HmacRefreshTokenHasherTest {
    @Test
    void matchesOnlyTheOriginalToken() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var hasher = new HmacRefreshTokenHasher(key);
        String hash = hasher.hash("random-token-a");
        assertTrue(hasher.matches("random-token-a", hash));
        assertFalse(hasher.matches("random-token-b", hash));
    }
}

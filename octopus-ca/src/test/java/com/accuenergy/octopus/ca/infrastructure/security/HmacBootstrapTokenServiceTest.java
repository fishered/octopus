package com.accuenergy.octopus.ca.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class HmacBootstrapTokenServiceTest {
    @Test
    void issuesOpaqueTokenAndHashesDeterministically() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        HmacBootstrapTokenService service = new HmacBootstrapTokenService(key);
        var issued = service.issue();
        assertEquals(issued.tokenHash(), service.hash(issued.rawToken()));
        assertNotEquals(issued.rawToken(), issued.tokenHash());
    }
}

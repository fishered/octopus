package com.accuenergy.octopus.mgmt.interfaces.auth;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public final class JwkSetController {
    private final RSAKey key;

    public JwkSetController(RSAKey key) {
        this.key = key;
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return Map.of("keys", java.util.List.of(key.toPublicJWK().toJSONObject()));
    }
}

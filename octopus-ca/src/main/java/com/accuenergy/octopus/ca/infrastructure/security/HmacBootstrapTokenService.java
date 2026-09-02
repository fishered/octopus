package com.accuenergy.octopus.ca.infrastructure.security;

import com.accuenergy.octopus.ca.application.BootstrapTokenService;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HmacBootstrapTokenService implements BootstrapTokenService {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public HmacBootstrapTokenService(@Value("${octopus.ca.bootstrap-hmac-key}") String base64Key) {
        key = Base64.getDecoder().decode(base64Key);
        if (key.length < 32) throw new IllegalArgumentException("Bootstrap HMAC key must be at least 256 bits");
    }

    @Override
    public IssuedToken issue() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        return new IssuedToken(raw, hash(raw));
    }

    @Override
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 500) {
            throw new IllegalArgumentException("Bootstrap token is invalid");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("HmacSHA256 is unavailable", unavailable);
        }
    }
}

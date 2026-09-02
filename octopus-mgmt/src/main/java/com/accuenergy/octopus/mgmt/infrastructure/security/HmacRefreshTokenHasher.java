package com.accuenergy.octopus.mgmt.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacRefreshTokenHasher {
    private final byte[] key;

    public HmacRefreshTokenHasher(String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
        if (key.length < 32) throw new IllegalArgumentException("Refresh-token HMAC key must be at least 256 bits");
    }

    public String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public boolean matches(String rawToken, String expectedHash) {
        return MessageDigest.isEqual(hash(rawToken).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}


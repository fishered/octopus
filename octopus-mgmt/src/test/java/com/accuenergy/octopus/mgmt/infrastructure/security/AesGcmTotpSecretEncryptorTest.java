package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmTotpSecretEncryptorTest {
    @Test
    void encryptsWithFreshNonceAndDecryptsEnvelope() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var encryption = new AesGcmTotpSecretDecryptor(key);
        byte[] secret = "01234567890123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        String first = encryption.encrypt(secret);
        String second = encryption.encrypt(secret);

        assertNotEquals(first, second);
        assertArrayEquals(secret, encryption.decrypt(first));
        assertArrayEquals(secret, encryption.decrypt(second));
    }
}

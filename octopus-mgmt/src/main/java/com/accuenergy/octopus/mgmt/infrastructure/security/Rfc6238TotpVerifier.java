package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.TotpVerifier;
import com.accuenergy.octopus.mgmt.application.identity.TotpEnrollmentVerifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 verifier; replay prevention is enforced separately by persisting the last accepted counter. */
public final class Rfc6238TotpVerifier implements TotpVerifier, TotpEnrollmentVerifier {
    private final TotpSecretDecryptor secrets;
    private final TotpCounterStore counters;
    private final int digits;
    private final int stepSeconds;
    private final int allowedWindow;
    private final String algorithm;

    public Rfc6238TotpVerifier(TotpSecretDecryptor secrets, TotpCounterStore counters) {
        this(secrets, counters, 6, 30, 1, "HmacSHA1");
    }

    Rfc6238TotpVerifier(TotpSecretDecryptor secrets, TotpCounterStore counters, int digits, int stepSeconds,
                       int allowedWindow, String algorithm) {
        this.secrets = secrets;
        this.counters = counters;
        this.digits = digits;
        this.stepSeconds = stepSeconds;
        this.allowedWindow = allowedWindow;
        this.algorithm = algorithm;
        if (digits < 6 || digits > 8 || stepSeconds <= 0 || allowedWindow < 0) {
            throw new IllegalArgumentException("Invalid TOTP policy");
        }
    }

    @Override
    public boolean verify(UUID factorId, String encryptedSecret, String code, Instant now) {
        OptionalLong matched = verifyCandidate(encryptedSecret, code, now);
        return matched.isPresent() && counters.tryAdvance(factorId, matched.getAsLong());
    }

    @Override
    public OptionalLong verifyCandidate(String encryptedSecret, String code, Instant now) {
        if (code == null || code.length() != digits || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] secret = secrets.decrypt(encryptedSecret);
        try {
            long counter = now.getEpochSecond() / stepSeconds;
            for (int offset = -allowedWindow; offset <= allowedWindow; offset++) {
                String expected = generate(secret, counter + offset);
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                        code.getBytes(StandardCharsets.US_ASCII))) {
                    return OptionalLong.of(counter + offset);
                }
            }
            return OptionalLong.empty();
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    private String generate(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret, algorithm));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int modulus = (int) Math.pow(10, digits);
            return String.format(java.util.Locale.ROOT, "%0" + digits + "d", binary % modulus);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP algorithm is unavailable", exception);
        }
    }
}

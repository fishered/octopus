package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.TotpSecretEncryptor;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Local/Kubernetes adapter. Production can replace this port with KMS envelope decryption. */
public final class AesGcmTotpSecretDecryptor implements TotpSecretDecryptor, TotpSecretEncryptor {
    private static final int NONCE_LENGTH = 12;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTotpSecretDecryptor(String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
        if (key.length != 32) throw new IllegalArgumentException("TOTP AES key must be exactly 256 bits");
    }

    @Override
    public String encrypt(byte[] secret) {
        if (secret == null || secret.length < 16) throw new IllegalArgumentException("TOTP secret is too short");
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        byte[] ciphertext = null;
        byte[] envelope = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            ciphertext = cipher.doFinal(secret);
            envelope = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP secret encryption failed", exception);
        } finally {
            Arrays.fill(nonce, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
            if (envelope != null) Arrays.fill(envelope, (byte) 0);
        }
    }

    @Override
    public byte[] decrypt(String encryptedSecret) {
        byte[] envelope = Base64.getDecoder().decode(encryptedSecret);
        if (envelope.length <= NONCE_LENGTH + 16) throw new IllegalArgumentException("Invalid TOTP secret envelope");
        byte[] nonce = Arrays.copyOfRange(envelope, 0, NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(envelope, NONCE_LENGTH, envelope.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new SecurityException("TOTP secret decryption failed", exception);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }
}

package com.accuenergy.octopus.mgmt.infrastructure.security;

public interface TotpSecretDecryptor {
    byte[] decrypt(String encryptedSecret);
}


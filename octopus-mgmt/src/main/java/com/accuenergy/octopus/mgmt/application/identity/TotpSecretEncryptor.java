package com.accuenergy.octopus.mgmt.application.identity;

public interface TotpSecretEncryptor {
    String encrypt(byte[] secret);
}

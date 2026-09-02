package com.accuenergy.octopus.ca.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPairGenerator;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

class BouncyCastleCsrProofVerifierTest {
    @Test
    void verifiesCsrSignatureAndRejectsTampering() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        var builder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=device-1"), pair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        byte[] encoded = builder.build(signer).getEncoded();
        BouncyCastleCsrProofVerifier verifier = new BouncyCastleCsrProofVerifier();
        assertTrue(verifier.verify(encoded));
        encoded[encoded.length - 1] ^= 1;
        assertFalse(verifier.verify(encoded));
    }
}

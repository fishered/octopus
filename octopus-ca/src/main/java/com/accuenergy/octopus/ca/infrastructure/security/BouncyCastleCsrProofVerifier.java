package com.accuenergy.octopus.ca.infrastructure.security;

import com.accuenergy.octopus.ca.application.ProofOfPossessionPort;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.stereotype.Component;

@Component
public final class BouncyCastleCsrProofVerifier implements ProofOfPossessionPort {
    @Override
    public boolean verify(byte[] certificateSigningRequest) {
        try {
            PKCS10CertificationRequest csr = new PKCS10CertificationRequest(certificateSigningRequest);
            var verifier = new JcaContentVerifierProviderBuilder()
                    .setProvider(new BouncyCastleProvider())
                    .build(csr.getSubjectPublicKeyInfo());
            return csr.isSignatureValid(verifier);
        } catch (Exception invalidCsr) {
            return false;
        }
    }
}

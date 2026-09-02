package com.accuenergy.octopus.ca.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface CertificateSigningPort {
    IssuedCertificate sign(SigningRequest request);

    record SigningRequest(byte[] certificateSigningRequest, List<String> subjectAlternativeNames,
                          Instant notBefore, Instant notAfter, String profile) {
        public SigningRequest {
            certificateSigningRequest = certificateSigningRequest.clone();
            subjectAlternativeNames = List.copyOf(subjectAlternativeNames);
        }
        @Override public byte[] certificateSigningRequest() { return certificateSigningRequest.clone(); }
    }

    record IssuedCertificate(UUID certificateId, String serialNumber, byte[] certificateChain,
                             Instant notBefore, Instant notAfter) {
        public IssuedCertificate {
            Objects.requireNonNull(certificateId, "certificateId");
            certificateChain = certificateChain.clone();
        }
        @Override public byte[] certificateChain() { return certificateChain.clone(); }
    }
}

package com.accuenergy.octopus.ca.application;

import java.time.Clock;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class IssueOperationalCertificateService {
    private final DeviceIdentityRepository identities;
    private final ProofOfPossessionPort proofOfPossession;
    private final CertificateSigningPort signing;
    private final Clock clock;

    public IssueOperationalCertificateService(DeviceIdentityRepository identities,
                                              ProofOfPossessionPort proofOfPossession,
                                              CertificateSigningPort signing, Clock clock) {
        this.identities = identities;
        this.proofOfPossession = proofOfPossession;
        this.signing = signing;
        this.clock = clock;
    }

    public CertificateSigningPort.IssuedCertificate issue(UUID tenantId, UUID identityId, byte[] csr,
                                                           Duration lifetime, String idempotencyKey) {
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero()
                || lifetime.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("Certificate lifetime must be between 1 second and 90 days");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        String csrFingerprint = sha256(csr);
        var existing = identities.findIssuedByIdempotency(tenantId, identityId, idempotencyKey);
        if (existing.isPresent()) {
            var prior = existing.orElseThrow();
            if (!csrFingerprint.equals(prior.csrFingerprint())) {
                throw new IllegalStateException("Idempotency-Key was already used with a different CSR");
            }
            return prior.certificate();
        }
        if (!proofOfPossession.verify(csr)) throw new SecurityException("CSR proof of possession failed");
        var identity = identities.findForTenant(tenantId, identityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown identity"));
        identity.tenantId().orElseThrow(() -> new IllegalStateException("Identity is not claimed"));
        UUID deviceId = identity.deviceId().orElseThrow(() -> new IllegalStateException("Identity has no device binding"));
        var now = clock.instant();
        var request = new CertificateSigningPort.SigningRequest(csr,
                List.of("urn:octopus:tenant:" + tenantId, "urn:octopus:device:" + deviceId,
                        "urn:octopus:device-identity:" + identityId),
                now.minusSeconds(60), now.plus(lifetime), "device-operational-v1");
        var certificate = signing.sign(request);
        identity.activate(certificate.serialNumber(), certificate.notAfter(), now);
        identities.saveIssued(identity, certificate, idempotencyKey, csrFingerprint, now);
        return certificate;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}

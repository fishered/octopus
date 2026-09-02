package com.accuenergy.octopus.ca.application;

import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeviceIdentityRepository {
    boolean hardwareSerialExists(String hardwareSerial);
    void create(DeviceIdentity identity, String bootstrapTokenHash, Instant bootstrapExpiresAt);
    Optional<DeviceIdentity> claim(UUID identityId, String bootstrapTokenHash, UUID tenantId, Instant now);
    Optional<DeviceIdentity> find(UUID identityId);
    Optional<DeviceIdentity> findForTenant(UUID tenantId, UUID identityId);
    Optional<CertificateSigningPort.IssuedCertificate> findIssuedByIdempotency(
            UUID tenantId, UUID identityId, String idempotencyKey);
    void saveIssued(DeviceIdentity identity, CertificateSigningPort.IssuedCertificate certificate,
                    String idempotencyKey, String csrFingerprint, Instant issuedAt);
    void revoke(DeviceIdentity identity, String reason, Instant revokedAt);
}

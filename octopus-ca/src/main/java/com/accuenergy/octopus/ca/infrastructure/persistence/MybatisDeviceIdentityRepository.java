package com.accuenergy.octopus.ca.infrastructure.persistence;

import com.accuenergy.octopus.ca.application.CertificateSigningPort;
import com.accuenergy.octopus.ca.application.DeviceCertificateAuthorization;
import com.accuenergy.octopus.ca.application.DeviceIdentityRepository;
import com.accuenergy.octopus.ca.domain.DeviceIdentity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceIdentityRepository implements DeviceIdentityRepository {
    private final CaIdentityMapper mapper;

    public MybatisDeviceIdentityRepository(CaIdentityMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public boolean hardwareSerialExists(String hardwareSerial) {
        return mapper.countHardwareSerial(hardwareSerial) > 0;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void create(DeviceIdentity identity, String bootstrapTokenHash, Instant bootstrapExpiresAt) {
        if (mapper.insertIdentity(toRow(identity)) != 1
                || mapper.insertBootstrap(identity.identityId(), bootstrapTokenHash,
                        bootstrapExpiresAt, identity.createdAt()) != 1) {
            throw new IllegalStateException("Unable to persist manufactured device identity");
        }
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public Optional<DeviceIdentity> claim(UUID identityId, String bootstrapTokenHash,
                                          UUID tenantId, UUID deviceId, Instant now) {
        CaIdentityMapper.IdentityRow row = mapper.findForUpdate(identityId);
        if (row == null) return Optional.empty();
        DeviceIdentity identity = toDomain(row);
        identity.claim(tenantId, deviceId, now);
        if (mapper.consumeBootstrap(identityId, bootstrapTokenHash, now) != 1) return Optional.empty();
        if (mapper.updateClaim(toRow(identity), row.version()) != 1) {
            throw new IllegalStateException("Concurrent device claim detected");
        }
        return Optional.of(identity);
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<DeviceIdentity> find(UUID identityId) {
        return Optional.ofNullable(mapper.findById(identityId)).map(MybatisDeviceIdentityRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<DeviceIdentity> findForTenant(UUID tenantId, UUID identityId) {
        return Optional.ofNullable(mapper.findForTenant(tenantId, identityId))
                .map(MybatisDeviceIdentityRepository::toDomain);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<IssuedCertificateRequest> findIssuedByIdempotency(
            UUID tenantId, UUID identityId, String idempotencyKey) {
        return Optional.ofNullable(mapper.findIssuedByIdempotency(tenantId, identityId, idempotencyKey))
                .map(row -> new IssuedCertificateRequest(
                        new CertificateSigningPort.IssuedCertificate(row.id(), row.serialNumber(),
                                row.certificateChain(), row.notBefore(), row.notAfter()),
                        row.csrFingerprint()));
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<DeviceCertificateAuthorization> findCertificateAuthorization(String certificateSerial, Instant now) {
        return Optional.ofNullable(mapper.findCertificateAuthorization(certificateSerial, now))
                .map(row -> new DeviceCertificateAuthorization(row.tenantId(), row.deviceId(),
                        row.serialNumber(), row.notAfter()));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void saveIssued(DeviceIdentity identity, CertificateSigningPort.IssuedCertificate certificate,
                           String idempotencyKey, String csrFingerprint, Instant issuedAt) {
        UUID tenantId = identity.tenantId().orElseThrow();
        CaIdentityMapper.CertificateRow certificateRow = new CaIdentityMapper.CertificateRow(certificate.certificateId(),
                tenantId, identity.identityId(), certificate.serialNumber(), certificate.certificateChain(),
                csrFingerprint, idempotencyKey, "ACTIVE", certificate.notBefore(), certificate.notAfter(), issuedAt);
        mapper.supersedeActiveCertificates(tenantId, identity.identityId(), issuedAt);
        if (mapper.insertCertificate(certificateRow) != 1
                || mapper.updateIssuedIdentity(toRow(identity), identity.version()) != 1) {
            throw new IllegalStateException("Unable to atomically persist issued certificate");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void revoke(DeviceIdentity identity, String reason, Instant revokedAt) {
        UUID tenantId = identity.tenantId().orElseThrow();
        mapper.revokeCertificates(tenantId, identity.identityId(), revokedAt, reason);
        if (mapper.updateRevokedIdentity(toRow(identity), identity.version()) != 1) {
            throw new IllegalStateException("Unable to revoke device identity");
        }
    }

    private static DeviceIdentity toDomain(CaIdentityMapper.IdentityRow row) {
        return DeviceIdentity.restore(row.identityId(), row.hardwareSerial(), row.manufacturer(),
                row.modelCode(), row.batchCode(), row.bootstrapPublicKeyFingerprint(),
                DeviceIdentity.Status.valueOf(row.status()), row.tenantId(), row.deviceId(),
                row.operationalCertificateSerial(),
                row.certificateExpiresAt(), row.claimedAt(), row.version(), row.createdAt(), row.updatedAt());
    }

    private static CaIdentityMapper.IdentityRow toRow(DeviceIdentity identity) {
        return new CaIdentityMapper.IdentityRow(identity.identityId(), identity.tenantId().orElse(null),
                identity.deviceId().orElse(null),
                identity.hardwareSerial(), identity.manufacturer(), identity.modelCode(), identity.batchCode(),
                identity.bootstrapPublicKeyFingerprint(), identity.status().name(),
                identity.operationalCertificateSerial().orElse(null), identity.certificateExpiresAt().orElse(null),
                identity.claimedAt().orElse(null), identity.version(), identity.createdAt(), identity.updatedAt());
    }
}

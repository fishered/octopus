package com.accuenergy.octopus.ca.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CaIdentityMapper {
    long countHardwareSerial(@Param("hardwareSerial") String hardwareSerial);
    int insertIdentity(@Param("identity") IdentityRow identity);
    int insertBootstrap(@Param("identityId") UUID identityId, @Param("tokenHash") String tokenHash,
                        @Param("expiresAt") Instant expiresAt, @Param("createdAt") Instant createdAt);
    IdentityRow findById(@Param("identityId") UUID identityId);
    IdentityRow findForTenant(@Param("tenantId") UUID tenantId, @Param("identityId") UUID identityId);
    IdentityRow findForUpdate(@Param("identityId") UUID identityId);
    int consumeBootstrap(@Param("identityId") UUID identityId, @Param("tokenHash") String tokenHash,
                         @Param("usedAt") Instant usedAt);
    int updateClaim(@Param("identity") IdentityRow identity, @Param("expectedVersion") long expectedVersion);
    CertificateRow findIssuedByIdempotency(@Param("tenantId") UUID tenantId,
                                           @Param("identityId") UUID identityId,
                                           @Param("idempotencyKey") String idempotencyKey);
    int insertCertificate(@Param("certificate") CertificateRow certificate);
    int supersedeActiveCertificates(@Param("tenantId") UUID tenantId, @Param("identityId") UUID identityId,
                                    @Param("supersededAt") Instant supersededAt);
    int updateIssuedIdentity(@Param("identity") IdentityRow identity,
                             @Param("expectedVersion") long expectedVersion);
    int revokeCertificates(@Param("tenantId") UUID tenantId, @Param("identityId") UUID identityId,
                           @Param("revokedAt") Instant revokedAt, @Param("reason") String reason);
    int updateRevokedIdentity(@Param("identity") IdentityRow identity,
                              @Param("expectedVersion") long expectedVersion);

    record IdentityRow(UUID identityId, UUID tenantId, String hardwareSerial, String manufacturer,
                       String modelCode, String batchCode, String bootstrapPublicKeyFingerprint,
                       String status, String operationalCertificateSerial, Instant certificateExpiresAt,
                       Instant claimedAt, long version, Instant createdAt, Instant updatedAt) { }

    record CertificateRow(UUID id, UUID tenantId, UUID identityId, String serialNumber,
                          byte[] certificateChain, String csrFingerprint, String idempotencyKey,
                          String status, Instant notBefore, Instant notAfter, Instant issuedAt) {
        public CertificateRow { certificateChain = certificateChain == null ? null : certificateChain.clone(); }
        @Override public byte[] certificateChain() {
            return certificateChain == null ? null : certificateChain.clone();
        }
    }
}

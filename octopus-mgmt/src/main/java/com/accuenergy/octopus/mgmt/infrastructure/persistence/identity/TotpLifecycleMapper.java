package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TotpLifecycleMapper {
    FactorRow findActive(@Param("accountId") UUID accountId);
    FactorRow findPending(@Param("accountId") UUID accountId, @Param("factorId") UUID factorId,
                          @Param("now") Instant now);
    FactorRow findCurrent(@Param("accountId") UUID accountId);
    int revokePending(@Param("accountId") UUID accountId, @Param("now") Instant now);
    int insertPending(@Param("factorId") UUID factorId, @Param("accountId") UUID accountId,
                      @Param("encryptedSecret") byte[] encryptedSecret,
                      @Param("keyVersion") String keyVersion, @Param("expiresAt") Instant expiresAt,
                      @Param("now") Instant now);
    int activate(@Param("factorId") UUID factorId, @Param("accountId") UUID accountId,
                 @Param("acceptedCounter") long acceptedCounter, @Param("now") Instant now);
    int insertRecoveryCode(@Param("id") UUID id, @Param("accountId") UUID accountId,
                           @Param("factorId") UUID factorId, @Param("codeHash") String codeHash,
                           @Param("now") Instant now);
    int revokeFactors(@Param("accountId") UUID accountId, @Param("reason") String reason,
                      @Param("now") Instant now);
    int revokeRecoveryCodes(@Param("accountId") UUID accountId, @Param("now") Instant now);
    int advanceSessionGeneration(@Param("accountId") UUID accountId,
                                 @Param("generation") long generation, @Param("now") Instant now);
    int insertAudit(@Param("id") UUID id, @Param("actorAccountId") UUID actorAccountId,
                    @Param("sessionId") UUID sessionId, @Param("eventType") String eventType,
                    @Param("accountId") UUID accountId, @Param("factorId") UUID factorId,
                    @Param("sessionGeneration") Long sessionGeneration,
                    @Param("recoveryCodeCount") Integer recoveryCodeCount,
                    @Param("reason") String reason, @Param("now") Instant now);
    List<RecoveryCodeRow> findActiveRecoveryCodes(@Param("accountId") UUID accountId,
                                                   @Param("factorId") UUID factorId);
    int consumeRecoveryCode(@Param("id") UUID id, @Param("now") Instant now);
    int insertRecoveryCodeUseAudit(@Param("id") UUID id, @Param("accountId") UUID accountId,
                                   @Param("factorId") UUID factorId,
                                   @Param("recoveryCodeId") UUID recoveryCodeId,
                                   @Param("now") Instant now);

    record FactorRow(UUID factorId, UUID accountId, byte[] encryptedSecret,
                     String status, Instant expiresAt) { }
    record RecoveryCodeRow(UUID id, String codeHash) { }
}

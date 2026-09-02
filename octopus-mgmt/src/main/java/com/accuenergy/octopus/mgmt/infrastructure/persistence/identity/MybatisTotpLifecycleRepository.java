package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import com.accuenergy.octopus.mgmt.application.identity.TotpLifecycleRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisTotpLifecycleRepository implements TotpLifecycleRepository {
    private final TotpLifecycleMapper mapper;

    public MybatisTotpLifecycleRepository(TotpLifecycleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<Factor> findActive(UUID accountId) {
        return Optional.ofNullable(mapper.findActive(accountId)).map(MybatisTotpLifecycleRepository::map);
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<Factor> findPending(UUID accountId, UUID factorId, Instant now) {
        return Optional.ofNullable(mapper.findPending(accountId, factorId, now))
                .map(MybatisTotpLifecycleRepository::map);
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public Optional<Factor> findCurrent(UUID accountId) {
        return Optional.ofNullable(mapper.findCurrent(accountId)).map(MybatisTotpLifecycleRepository::map);
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public UUID createPending(UUID accountId, String encryptedSecret, String keyVersion,
                              UUID actorAccountId, UUID sessionId, Instant expiresAt, Instant now) {
        mapper.revokePending(accountId, now);
        UUID factorId = UUID.randomUUID();
        if (mapper.insertPending(factorId, accountId, Base64.getDecoder().decode(encryptedSecret),
                keyVersion, expiresAt, now) != 1) {
            throw new IllegalStateException("Unable to create TOTP enrollment");
        }
        audit("TOTP_ENROLLMENT_STARTED", actorAccountId, sessionId, accountId, factorId,
                null, null, "Self-service TOTP enrollment", now);
        return factorId;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void activate(Factor factor, long acceptedCounter, List<String> recoveryCodeHashes,
                         long sessionGeneration, UUID actorAccountId, UUID sessionId, Instant now) {
        if (mapper.activate(factor.factorId(), factor.accountId(), acceptedCounter, now) != 1) {
            throw new IllegalStateException("TOTP enrollment expired or was already used");
        }
        for (String hash : recoveryCodeHashes) {
            if (mapper.insertRecoveryCode(UUID.randomUUID(), factor.accountId(), factor.factorId(), hash, now) != 1) {
                throw new IllegalStateException("Unable to persist TOTP recovery code");
            }
        }
        advanceGeneration(factor.accountId(), sessionGeneration, now);
        audit("TOTP_ACTIVATED", actorAccountId, sessionId, factor.accountId(), factor.factorId(),
                sessionGeneration, recoveryCodeHashes.size(), "Self-service TOTP activation", now);
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public void revoke(UUID accountId, long sessionGeneration, UUID actorAccountId, UUID sessionId,
                       String eventType, String reason, Instant now) {
        if (mapper.revokeFactors(accountId, reason, now) < 1) {
            throw new IllegalStateException("TOTP factor was concurrently removed");
        }
        mapper.revokeRecoveryCodes(accountId, now);
        advanceGeneration(accountId, sessionGeneration, now);
        audit(eventType, actorAccountId, sessionId, accountId, null,
                sessionGeneration, null, reason, now);
    }

    private void advanceGeneration(UUID accountId, long sessionGeneration, Instant now) {
        if (mapper.advanceSessionGeneration(accountId, sessionGeneration, now) != 1) {
            throw new IllegalStateException("Unable to persist account session generation");
        }
    }

    private void audit(String eventType, UUID actorAccountId, UUID sessionId, UUID accountId,
                       UUID factorId, Long generation, Integer recoveryCodeCount,
                       String reason, Instant now) {
        if (mapper.insertAudit(UUID.randomUUID(), actorAccountId, sessionId, eventType, accountId,
                factorId, generation, recoveryCodeCount, reason, now) != 1) {
            throw new IllegalStateException("Unable to audit TOTP lifecycle change");
        }
    }

    private static Factor map(TotpLifecycleMapper.FactorRow row) {
        return new Factor(row.factorId(), row.accountId(),
                Base64.getEncoder().encodeToString(row.encryptedSecret()),
                Status.valueOf(row.status()), Optional.ofNullable(row.expiresAt()));
    }
}

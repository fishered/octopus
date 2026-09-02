package com.accuenergy.octopus.mgmt.infrastructure.security;

import com.accuenergy.octopus.mgmt.application.identity.PasswordVerifier;
import com.accuenergy.octopus.mgmt.application.identity.RecoveryCodeVerifier;
import com.accuenergy.octopus.mgmt.infrastructure.persistence.identity.TotpLifecycleMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class PostgresRecoveryCodeVerifier implements RecoveryCodeVerifier {
    private final TotpLifecycleMapper mapper;
    private final PasswordVerifier passwords;

    public PostgresRecoveryCodeVerifier(TotpLifecycleMapper mapper, PasswordVerifier passwords) {
        this.mapper = mapper;
        this.passwords = passwords;
    }

    @Override
    @Transactional(transactionManager = "platformTransactionManager")
    public boolean consume(UUID accountId, UUID factorId, String recoveryCode, Instant now) {
        if (recoveryCode == null || recoveryCode.isBlank() || recoveryCode.length() > 64) return false;
        char[] normalized = recoveryCode.strip().toUpperCase(Locale.ROOT).toCharArray();
        try {
            for (TotpLifecycleMapper.RecoveryCodeRow row : mapper.findActiveRecoveryCodes(accountId, factorId)) {
                if (passwords.matches(normalized, row.codeHash())
                        && mapper.consumeRecoveryCode(row.id(), now) == 1) {
                    if (mapper.insertRecoveryCodeUseAudit(UUID.randomUUID(), accountId, factorId,
                            row.id(), now) != 1) {
                        throw new IllegalStateException("Unable to audit recovery code use");
                    }
                    return true;
                }
            }
            return false;
        } finally {
            Arrays.fill(normalized, '\0');
        }
    }
}

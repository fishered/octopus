package com.accuenergy.octopus.mgmt.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.mgmt.infrastructure.persistence.identity.TotpLifecycleMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PostgresRecoveryCodeVerifierTest {
    @Test
    void recoveryCodeIsMatchedAgainstHashAndConsumedExactlyOnce() {
        UUID accountId = UUID.randomUUID();
        UUID factorId = UUID.randomUUID();
        UUID codeId = UUID.randomUUID();
        AtomicBoolean consumed = new AtomicBoolean();
        AtomicInteger audits = new AtomicInteger();
        TotpLifecycleMapper mapper = (TotpLifecycleMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {TotpLifecycleMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findActiveRecoveryCodes" -> consumed.get() ? List.of()
                            : List.of(new TotpLifecycleMapper.RecoveryCodeRow(codeId, "stored-hash"));
                    case "consumeRecoveryCode" -> consumed.compareAndSet(false, true) ? 1 : 0;
                    case "insertRecoveryCodeUseAudit" -> { audits.incrementAndGet(); yield 1; }
                    default -> throw new AssertionError("Unexpected mapper call " + method.getName());
                });
        var verifier = new PostgresRecoveryCodeVerifier(mapper,
                (presented, hash) -> hash.equals("stored-hash")
                        && new String(presented).equals("ABCD-EFGH-JKLM"));

        assertTrue(verifier.consume(accountId, factorId, "abcd-efgh-jklm", Instant.EPOCH));
        assertFalse(verifier.consume(accountId, factorId, "ABCD-EFGH-JKLM", Instant.EPOCH.plusSeconds(1)));
        assertEquals(1, audits.get());
    }
}

package com.accuenergy.octopus.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantAiBudgetTest {
    @Test
    void enforcesRequestAndEstimatedTokenLimitsPerTenant() {
        var budget = new TenantAiBudget(Clock.fixed(Instant.parse("2026-10-11T00:00:20Z"), ZoneOffset.UTC), 2, 10);
        UUID tenant = UUID.randomUUID();

        assertEquals(1, budget.reserve(tenant, 4).requestsUsed());
        assertEquals(2, budget.reserve(tenant, 6).requestsUsed());
        assertThrows(AiBudgetExceededException.class, () -> budget.reserve(tenant, 1));
        assertEquals(1, budget.reserve(UUID.randomUUID(), 10).requestsUsed());
    }
}

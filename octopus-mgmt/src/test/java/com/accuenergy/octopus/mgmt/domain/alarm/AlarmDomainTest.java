package com.accuenergy.octopus.mgmt.domain.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlarmDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void greaterThanRuleUsesClearThresholdAsHysteresis() {
        AlarmRule rule = rule(AlarmRule.Comparison.GREATER_THAN, "100", "95");

        assertTrue(rule.isTriggered(new BigDecimal("101")));
        assertFalse(rule.shouldClear(new BigDecimal("98")));
        assertTrue(rule.shouldClear(new BigDecimal("95")));
    }

    @Test
    void rejectsHysteresisInTheWrongDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> rule(AlarmRule.Comparison.GREATER_THAN, "100", "101"));
    }

    @Test
    void acknowledgedIncidentCanClearButLateDataCannotRollItBack() {
        AlarmRule rule = rule(AlarmRule.Comparison.GREATER_THAN, "100", "95");
        AlarmIncident incident = AlarmIncident.open(UUID.randomUUID(), rule, new BigDecimal("110"), NOW, NOW);
        UUID accountId = UUID.randomUUID();
        incident.acknowledge(accountId, NOW.plusSeconds(1));

        assertFalse(incident.clear(new BigDecimal("90"), NOW.minusSeconds(1), NOW.plusSeconds(2)));
        assertEquals(AlarmIncident.State.ACKNOWLEDGED, incident.state());
        assertTrue(incident.clear(new BigDecimal("90"), NOW.plusSeconds(2), NOW.plusSeconds(3)));
        assertEquals(AlarmIncident.State.CLEARED, incident.state());
        assertEquals(accountId, incident.acknowledgedBy().orElseThrow());
    }

    private static AlarmRule rule(AlarmRule.Comparison comparison, String trigger, String clear) {
        return AlarmRule.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "high-load", "High load", AlarmRule.ValueSelector.RAW,
                comparison, new BigDecimal(trigger), new BigDecimal(clear), AlarmRule.Severity.WARNING, NOW);
    }
}

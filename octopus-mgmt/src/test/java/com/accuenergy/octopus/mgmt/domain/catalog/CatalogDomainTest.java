package com.accuenergy.octopus.mgmt.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import com.accuenergy.octopus.mgmt.domain.meter.Meter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void affineUnitConvertsToCanonicalValue() {
        UnitDefinition celsius = new UnitDefinition(UUID.randomUUID(), "Cel", "°C", "temperature",
                BigDecimal.ONE, new BigDecimal("273.15"), Optional.empty());
        assertEquals(0, celsius.toCanonical(BigDecimal.ZERO).compareTo(new BigDecimal("273.15")));
    }

    @Test
    void nonNumericParameterCannotCarryUnitMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterDefinition(UUID.randomUUID(),
                Optional.empty(), "switch", "Switch", "state", ValueSemantics.INSTANTANEOUS,
                ParameterDefinition.DataType.BOOLEAN, Optional.of(UUID.randomUUID()), Optional.empty(),
                ParameterDefinition.Status.ACTIVE));
    }

    @Test
    void publishedThingModelRequiresUniqueBindingsAndBecomesImmutableByLifecycle() {
        UUID parameter = UUID.randomUUID();
        ThingModel model = ThingModel.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "energy-meter", "Energy meter", 1, "{}",
                List.of(new ThingModel.ParameterBinding(parameter, UUID.randomUUID(), true, 0)), NOW);
        model.publish(NOW.plusSeconds(10));
        assertEquals(ThingModel.Status.PUBLISHED, model.status());
        assertThrows(IllegalStateException.class, () -> model.publish(NOW.plusSeconds(20)));
    }

    @Test
    void thingModelBindingsAreReadOnlyByDefaultAndExplicitlyDeclareWritableAccess() {
        UUID parameter = UUID.randomUUID();
        ThingModel.ParameterBinding legacy = new ThingModel.ParameterBinding(
                parameter, UUID.randomUUID(), true, 0);
        ThingModel.ParameterBinding writable = new ThingModel.ParameterBinding(
                parameter, UUID.randomUUID(), true, 0, ThingModel.AccessMode.READ_WRITE);

        assertEquals(ThingModel.AccessMode.READ_ONLY, legacy.accessMode());
        assertFalse(legacy.accessMode().writable());
        assertTrue(writable.accessMode().writable());
    }

    @Test
    void calculatedMeterRequiresExpression() {
        assertThrows(IllegalArgumentException.class, () -> Meter.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), "total", "Total",
                Meter.Kind.CALCULATED, null, null, 3, ValueSemantics.INTERVAL_DELTA, NOW));
    }

    @Test
    void rolloverOnlyAppliesToCumulativeParameters() {
        assertThrows(IllegalArgumentException.class, () -> Meter.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), "power", "Power",
                Meter.Kind.STANDARD, null, new BigDecimal("999999"), 3,
                ValueSemantics.INSTANTANEOUS, NOW));
    }
}

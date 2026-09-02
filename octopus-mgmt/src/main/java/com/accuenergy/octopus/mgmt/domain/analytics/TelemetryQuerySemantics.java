package com.accuenergy.octopus.mgmt.domain.analytics;

import com.accuenergy.octopus.mgmt.domain.catalog.ParameterDefinition.ValueSemantics;
import java.util.Objects;

/** Shared semantic validation for interactive queries and persisted dashboard widgets. */
public final class TelemetryQuerySemantics {
    private TelemetryQuerySemantics() { }

    public static void validate(ValueSemantics semantics, TelemetryQuery.ValueField field) {
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(field, "field");
        if (field == TelemetryQuery.ValueField.DELTA && semantics != ValueSemantics.CUMULATIVE) {
            throw new IllegalArgumentException("DELTA is only available for cumulative meters");
        }
        if (field == TelemetryQuery.ValueField.INTERVAL_ACCUMULATION
                && semantics != ValueSemantics.CUMULATIVE && semantics != ValueSemantics.INTERVAL_DELTA) {
            throw new IllegalArgumentException("Interval accumulation is unavailable for this meter semantics");
        }
    }
}

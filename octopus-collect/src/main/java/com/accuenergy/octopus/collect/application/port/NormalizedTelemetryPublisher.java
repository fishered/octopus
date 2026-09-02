package com.accuenergy.octopus.collect.application.port;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;

/** Publishes the normalized, calculation-complete stream for alarms and downstream consumers. */
public interface NormalizedTelemetryPublisher {
    void publish(NormalizedTelemetry telemetry);
}

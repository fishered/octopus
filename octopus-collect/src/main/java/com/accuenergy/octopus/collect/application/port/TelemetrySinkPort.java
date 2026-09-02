package com.accuenergy.octopus.collect.application.port;

import com.accuenergy.octopus.api.telemetry.NormalizedTelemetry;

public interface TelemetrySinkPort {
    void write(NormalizedTelemetry telemetry);
}


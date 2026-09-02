package com.accuenergy.octopus.collect.application.port;

import com.accuenergy.octopus.api.telemetry.TelemetryReading;

public interface QuarantinePort {
    void quarantine(TelemetryReading reading, String reason);
}


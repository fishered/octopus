package com.accuenergy.octopus.control.application.port;

import com.accuenergy.octopus.api.telemetry.TelemetryReading;

public interface TelemetryPayloadDecoder {
    TelemetryReading decode(byte[] payload);
}

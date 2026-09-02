package com.accuenergy.octopus.control.infrastructure.telemetry;

import com.accuenergy.octopus.api.telemetry.TelemetryReading;
import com.accuenergy.octopus.control.application.port.TelemetryPayloadDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class JacksonTelemetryPayloadDecoder implements TelemetryPayloadDecoder {
    private final ObjectMapper objectMapper;

    public JacksonTelemetryPayloadDecoder(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public TelemetryReading decode(byte[] payload) {
        try {
            return objectMapper.readValue(payload, TelemetryReading.class);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Telemetry payload is not a valid contract", malformed);
        }
    }
}

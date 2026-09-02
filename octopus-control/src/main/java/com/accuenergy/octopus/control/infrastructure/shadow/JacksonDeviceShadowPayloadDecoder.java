package com.accuenergy.octopus.control.infrastructure.shadow;

import com.accuenergy.octopus.control.application.port.DeviceShadowPayloadDecoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class JacksonDeviceShadowPayloadDecoder implements DeviceShadowPayloadDecoder {
    private final ObjectMapper objectMapper;

    public JacksonDeviceShadowPayloadDecoder(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public DecodedShadow decode(byte[] payload) {
        try {
            Payload decoded = objectMapper.readValue(payload, Payload.class);
            if (decoded.state() == null || !decoded.state().isObject()) {
                throw new IllegalArgumentException("Shadow state must be a JSON object");
            }
            return new DecodedShadow(decoded.schemaVersion(), decoded.reportId(), decoded.tenantId(),
                    decoded.deviceId(), decoded.shadowVersion(), objectMapper.writeValueAsString(decoded.state()),
                    decoded.reportedAt(), decoded.appliedDesiredVersion());
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("Shadow payload is not valid JSON", invalidJson);
        }
    }

    private record Payload(int schemaVersion, UUID reportId, UUID tenantId, UUID deviceId,
            long shadowVersion, JsonNode state, Instant reportedAt, Long appliedDesiredVersion) { }
}

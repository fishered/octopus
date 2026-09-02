package com.accuenergy.octopus.control.infrastructure.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JacksonDeviceShadowPayloadDecoderTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JacksonDeviceShadowPayloadDecoder decoder = new JacksonDeviceShadowPayloadDecoder(objectMapper);

    @Test
    void decodesObjectStateToCanonicalJson() {
        UUID reportId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        String payload = """
                {"schemaVersion":1,"reportId":"%s","tenantId":"%s","deviceId":"%s",
                 "shadowVersion":4,"state":{"relay":true},"reportedAt":"2026-01-01T00:00:00Z"}
                """.formatted(reportId, tenantId, deviceId);

        var decoded = decoder.decode(payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(reportId, decoded.reportId());
        assertEquals(4, decoded.shadowVersion());
        assertEquals("{\"relay\":true}", decoded.stateJson());
    }

    @Test
    void rejectsNonObjectState() {
        String payload = """
                {"schemaVersion":1,"reportId":"%s","tenantId":"%s","deviceId":"%s",
                 "shadowVersion":1,"state":[1,2],"reportedAt":"2026-01-01T00:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

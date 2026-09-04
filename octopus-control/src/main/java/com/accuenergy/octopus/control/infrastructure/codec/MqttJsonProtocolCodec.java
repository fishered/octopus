package com.accuenergy.octopus.control.infrastructure.codec;

import com.accuenergy.octopus.iot.spi.InboundMessage;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.OutboundMessage;
import com.accuenergy.octopus.iot.spi.ProtocolCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** JSON codec shared by MQTT inbound validation and outbound command encoding. */
@Component
public final class MqttJsonProtocolCodec implements ProtocolCodec {
    private final ObjectMapper objectMapper;

    public MqttJsonProtocolCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public String codecId() { return "json"; }

    @Override
    public boolean supports(InboundMessage message, MessageKind kind) {
        return message.contentType().equalsIgnoreCase("application/json")
                || message.contentType().equalsIgnoreCase("text/json")
                || message.topic().startsWith("octopus/");
    }

    @Override
    public DecodedPayload decode(InboundMessage message, MessageKind kind) {
        if (!supports(message, kind)) throw new IllegalArgumentException("MQTT JSON codec does not support message");
        try {
            JsonNode value = objectMapper.readTree(message.payload());
            if (value == null || !value.isObject()) throw new IllegalArgumentException("JSON payload must be an object");
            return new DecodedPayload(kind, value);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("Payload is not valid JSON", invalidJson);
        }
    }

    @Override
    public byte[] encode(OutboundMessage message) {
        try {
            JsonNode value = objectMapper.readTree(message.payload());
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("MQTT command payload must be a JSON object");
            }
            return objectMapper.writeValueAsBytes(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("MQTT command payload is not valid JSON", invalidJson);
        }
    }
}

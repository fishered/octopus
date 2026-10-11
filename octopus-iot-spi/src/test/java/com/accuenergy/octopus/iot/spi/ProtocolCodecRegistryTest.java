package com.accuenergy.octopus.iot.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProtocolCodecRegistryTest {
    @Test
    void selectsOnlyAPluginDeclaredSupportingCodec() {
        ProtocolCodec json = codec("json", "application/json");
        ProtocolCodec protobuf = codec("protobuf", "application/protobuf");
        var registry = new ProtocolCodecRegistry(List.of(json, protobuf));
        var message = new InboundMessage("mqtt-json", "topic", new byte[] {1}, 1, false,
                Instant.now(), "application/json");

        assertEquals(json, registry.select(message, MessageKind.TELEMETRY, Set.of("json", "protobuf")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.select(message, MessageKind.TELEMETRY, Set.of("protobuf")));
    }

    @Test
    void rejectsAmbiguousCodecMatches() {
        var registry = new ProtocolCodecRegistry(List.of(codec("first", "*"), codec("second", "*")));
        var message = new InboundMessage("vendor", "topic", new byte[] {1}, 1, false,
                Instant.now(), "application/octet-stream");

        assertThrows(IllegalArgumentException.class,
                () -> registry.select(message, MessageKind.TELEMETRY, Set.of("first", "second")));
    }

    private static ProtocolCodec codec(String id, String contentType) {
        return new ProtocolCodec() {
            @Override public String codecId() { return id; }
            @Override public boolean supports(InboundMessage message, MessageKind kind) {
                return contentType.equals("*") || contentType.equals(message.contentType());
            }
            @Override public DecodedPayload decode(InboundMessage message, MessageKind kind) {
                return new DecodedPayload(kind, message.payload());
            }
            @Override public byte[] encode(OutboundMessage message) { return message.payload(); }
        };
    }
}

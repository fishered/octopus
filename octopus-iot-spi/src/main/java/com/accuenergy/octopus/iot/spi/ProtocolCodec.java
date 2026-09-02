package com.accuenergy.octopus.iot.spi;

/** Optional protocol layer kept separate from the transport (MQTT, HTTP, or AWS IoT). */
public interface ProtocolCodec {
    String codecId();

    boolean supports(InboundMessage message, MessageKind kind);

    DecodedPayload decode(InboundMessage message, MessageKind kind);

    byte[] encode(OutboundMessage message);

    record DecodedPayload(MessageKind kind, Object value) {
        public DecodedPayload {
            if (kind == null || value == null) throw new NullPointerException("decoded payload fields are required");
        }
    }
}

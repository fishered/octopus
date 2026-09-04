package com.accuenergy.octopus.control.infrastructure.mqtt;

import com.accuenergy.octopus.control.application.DeviceShadowIngressService;
import com.accuenergy.octopus.control.application.TelemetryIngressService;
import com.accuenergy.octopus.control.domain.shadow.DeviceShadowTopic;
import com.accuenergy.octopus.iot.spi.InboundMessage;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.ProtocolCodec;
import com.accuenergy.octopus.iot.spi.ProtocolCodecRegistry;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Component;

/** Converts transport-neutral MQTT messages into the existing durable ingress workflows. */
@Component
public final class InboundMessageDispatcher {
    private final ProtocolCodecRegistry codecs;
    private final TelemetryIngressService telemetry;
    private final DeviceShadowIngressService shadows;
    private final PahoCommandResultHandler commandResults;

    public InboundMessageDispatcher(ProtocolCodecRegistry codecs, TelemetryIngressService telemetry,
            DeviceShadowIngressService shadows, PahoCommandResultHandler commandResults) {
        this.codecs = codecs;
        this.telemetry = telemetry;
        this.shadows = shadows;
        this.commandResults = commandResults;
    }

    public CompletionStage<Void> dispatch(InboundMessage message) {
        MessageKind kind = kind(message.topic());
        ProtocolCodec codec = codecs.require("json");
        if (!codec.supports(message, kind)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No codec supports inbound MQTT message"));
        }
        codec.decode(message, kind);
        if (kind == MessageKind.COMMAND_RESULT) {
            try {
                commandResults.handle(message.topic(), message.payload());
                return CompletableFuture.completedFuture(null);
            } catch (Exception failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        if (kind == MessageKind.SHADOW_REPORTED) {
            return shadows.ingest(message.payload(), new DeviceShadowIngressService.UplinkMetadata(
                    message.topic(), message.qos(), message.retained())).thenApply(ignored -> null);
        }
        return telemetry.ingest(message.payload(), new TelemetryIngressService.UplinkMetadata(
                message.topic(), message.qos(), message.retained())).thenApply(ignored -> null);
    }

    private static MessageKind kind(String topic) {
        if (PahoCommandResultHandler.isCommandResultTopic(topic)) return MessageKind.COMMAND_RESULT;
        if (DeviceShadowTopic.matches(topic)) return MessageKind.SHADOW_REPORTED;
        return MessageKind.TELEMETRY;
    }
}

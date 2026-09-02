package com.accuenergy.octopus.control.infrastructure.mqtt;

import com.accuenergy.octopus.control.application.port.DeviceMessagingPort;
import com.accuenergy.octopus.control.application.DeviceMessagingUnavailableException;
import com.accuenergy.octopus.iot.spi.DeviceTransportPlugin;
import com.accuenergy.octopus.iot.spi.InboundMessageHandler;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.OutboundMessage;
import com.accuenergy.octopus.iot.spi.PluginDescriptor;
import com.accuenergy.octopus.iot.spi.PluginHealth;
import com.accuenergy.octopus.iot.spi.PublishReceipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** MQTT 3.1.1 command adapter. AWS IoT implements the same application port. */
@Component
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public final class PahoDeviceMessagingAdapter implements DeviceTransportPlugin {
    private final MqttAsyncClient client;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public PahoDeviceMessagingAdapter(MqttAsyncClient client, Clock clock, ObjectMapper objectMapper) {
        this.client = client;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor("mqtt-json", "1.0.0", 1,
                Set.of(MessageKind.COMMAND, MessageKind.TELEMETRY, MessageKind.SHADOW_REPORTED,
                        MessageKind.COMMAND_RESULT), "{\"type\":\"object\"}");
    }

    @Override
    public void start(InboundMessageHandler inboundHandler) {
        if (inboundHandler == null) throw new NullPointerException("inboundHandler");
        // PahoTelemetryUplinkAdapter owns the shared client subscription lifecycle.
    }

    @Override
    public CompletionStage<PublishReceipt> publish(OutboundMessage message) {
        if (message.kind() != MessageKind.COMMAND) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("MQTT plugin only publishes COMMAND messages"));
        }
        try {
            DeviceMessagingPort.CommandReceipt receipt = send(new DeviceMessagingPort.DeviceCommand(
                    new com.accuenergy.octopus.api.control.DeviceCommandEnvelope(1,
                            message.correlationId(), message.tenantId(), message.deviceId(), message.operation(),
                            message.payload(), message.requestedAt(), message.expiresAt())));
            return CompletableFuture.completedFuture(new PublishReceipt(receipt.commandId(),
                    PublishReceipt.Status.valueOf(receipt.status().name()), receipt.acceptedAt(), "mqtt"));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private DeviceMessagingPort.CommandReceipt send(DeviceMessagingPort.DeviceCommand command) {
        var now = clock.instant();
        var envelope = command.envelope();
        if (!envelope.expiresAt().isAfter(now)) {
            return new DeviceMessagingPort.CommandReceipt(envelope.commandId(),
                    DeviceMessagingPort.CommandReceipt.Status.EXPIRED, now);
        }
        try {
            String topic = "octopus/" + envelope.tenantId() + "/devices/" + envelope.deviceId()
                    + "/commands/" + envelope.operation();
            client.publish(topic, objectMapper.writeValueAsBytes(envelope), 1, false).waitForCompletion(10_000);
            return new DeviceMessagingPort.CommandReceipt(envelope.commandId(),
                    DeviceMessagingPort.CommandReceipt.Status.ACCEPTED, now);
        } catch (JsonProcessingException invalidEnvelope) {
            throw new IllegalStateException("Unable to serialize command envelope", invalidEnvelope);
        } catch (Exception unavailable) {
            throw new DeviceMessagingUnavailableException("MQTT command publish failed", unavailable);
        }
    }

    @Override
    public PluginHealth health() {
        boolean connected = client.isConnected();
        return new PluginHealth(connected ? PluginHealth.Status.UP : PluginHealth.Status.DOWN,
                clock.instant(), connected ? "connected" : "disconnected");
    }

    @Override
    public void stop() {
        // The shared client is stopped by PahoTelemetryUplinkAdapter.
    }
}

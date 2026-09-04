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
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** MQTT 3.1.1 command adapter. AWS IoT implements the same application port. */
@Component
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public final class PahoDeviceMessagingAdapter implements DeviceTransportPlugin, org.springframework.context.SmartLifecycle,
        MqttCallbackExtended {
    private final MqttAsyncClient client;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final MqttConnectOptions connectOptions;
    private final InboundMessageDispatcher dispatcher;
    private final String topicFilter;
    private final String commandResultFilter;
    private final String shadowReportedFilter;
    private final AtomicBoolean running = new AtomicBoolean();

    public PahoDeviceMessagingAdapter(MqttAsyncClient client, Clock clock, ObjectMapper objectMapper,
            MqttConnectOptions connectOptions, InboundMessageDispatcher dispatcher,
            @org.springframework.beans.factory.annotation.Value("${octopus.mqtt.topic-filter}") String topicFilter,
            @org.springframework.beans.factory.annotation.Value("${octopus.mqtt.command-result-topic-filter}") String commandResultFilter,
            @org.springframework.beans.factory.annotation.Value("${octopus.mqtt.shadow-reported-topic-filter}") String shadowReportedFilter) {
        this.client = client;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.connectOptions = connectOptions;
        this.dispatcher = dispatcher;
        this.topicFilter = topicFilter;
        this.commandResultFilter = commandResultFilter;
        this.shadowReportedFilter = shadowReportedFilter;
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
    }

    @Override public void start() {
        if (!running.compareAndSet(false, true)) return;
        try {
            client.setCallback(this);
            client.setManualAcks(true);
            client.connect(connectOptions).waitForCompletion(30_000);
            subscribe();
        } catch (Exception failure) {
            running.set(false);
            throw new IllegalStateException("Unable to start MQTT transport plugin", failure);
        }
    }

    private void subscribe() throws Exception {
        client.subscribe(new String[]{topicFilter, commandResultFilter, shadowReportedFilter},
                new int[]{1, 1, 1}).waitForCompletion(30_000);
    }

    @Override public void messageArrived(String topic, MqttMessage message) {
        dispatcher.dispatch(new com.accuenergy.octopus.iot.spi.InboundMessage("mqtt", topic,
                message.getPayload(), message.getQos(), message.isRetained(), clock.instant(), "application/json"))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("MQTT inbound handoff failed for {}", topic, failure);
                        return;
                    }
                    try {
                        if (message.getQos() > 0) client.messageArrivedComplete(message.getId(), message.getQos());
                    } catch (Exception ackFailure) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Unable to ACK MQTT message {}", message.getId(), ackFailure);
                    }
                });
    }

    @Override public void connectComplete(boolean reconnect, String serverUri) {
        if (reconnect && running.get()) try { subscribe(); }
        catch (Exception failure) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Unable to restore MQTT subscriptions", failure); }
    }
    @Override public void connectionLost(Throwable cause) { }
    @Override public void deliveryComplete(IMqttDeliveryToken token) { }

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
        if (!running.compareAndSet(true, false)) return;
        try {
            if (client.isConnected()) {
                client.unsubscribe(new String[]{topicFilter, commandResultFilter, shadowReportedFilter})
                        .waitForCompletion(10_000);
                client.disconnect(30_000).waitForCompletion(30_000);
            }
            client.close();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to stop MQTT transport plugin", failure);
        }
    }

    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return 0; }
}

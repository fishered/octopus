package com.accuenergy.octopus.control.infrastructure.mqtt;

import com.accuenergy.octopus.control.application.TelemetryIngressService;
import com.accuenergy.octopus.control.application.DeviceShadowIngressService;
import com.accuenergy.octopus.control.domain.shadow.DeviceShadowTopic;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** MQTT source; PUBACK/PUBCOMP is sent only after Kafka acknowledges raw or quarantine storage. */
@Component
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public final class PahoTelemetryUplinkAdapter implements SmartLifecycle, MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(PahoTelemetryUplinkAdapter.class);
    private final MqttAsyncClient client;
    private final MqttConnectOptions connectOptions;
    private final TelemetryIngressService ingress;
    private final String topicFilter;
    private final String commandResultFilter;
    private final String shadowReportedFilter;
    private final PahoCommandResultHandler commandResults;
    private final DeviceShadowIngressService shadows;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object drainMonitor = new Object();

    public PahoTelemetryUplinkAdapter(MqttAsyncClient client, MqttConnectOptions connectOptions,
            TelemetryIngressService ingress, PahoCommandResultHandler commandResults,
            DeviceShadowIngressService shadows,
            @Value("${octopus.mqtt.topic-filter}") String topicFilter,
            @Value("${octopus.mqtt.command-result-topic-filter}") String commandResultFilter,
            @Value("${octopus.mqtt.shadow-reported-topic-filter}") String shadowReportedFilter) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.ingress = ingress;
        this.commandResults = commandResults;
        this.shadows = shadows;
        this.topicFilter = topicFilter;
        this.commandResultFilter = commandResultFilter;
        this.shadowReportedFilter = shadowReportedFilter;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        try {
            client.setCallback(this);
            client.setManualAcks(true);
            client.connect(connectOptions).waitForCompletion(30_000);
            client.subscribe(new String[]{topicFilter, commandResultFilter, shadowReportedFilter}, new int[]{1, 1, 1})
                    .waitForCompletion(30_000);
            log.info("MQTT ingress subscribed to telemetry {}, command results {}, and shadow reports {}",
                    topicFilter, commandResultFilter, shadowReportedFilter);
        } catch (Exception startupFailure) {
            running.set(false);
            throw new IllegalStateException("Unable to start MQTT telemetry ingress", startupFailure);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (commandResults.supports(topic)) {
            handleCommandResult(topic, message);
            return;
        }
        if (DeviceShadowTopic.matches(topic)) {
            handleShadowReport(topic, message);
            return;
        }
        inFlight.incrementAndGet();
        var metadata = new TelemetryIngressService.UplinkMetadata(topic, message.getQos(), message.isRetained());
        ingress.ingest(message.getPayload(), metadata).whenComplete((outcome, failure) -> {
            try {
                if (failure == null) {
                    if (message.getQos() > 0) {
                        client.messageArrivedComplete(message.getId(), message.getQos());
                    }
                } else {
                    log.warn("Kafka handoff failed for MQTT topic {}; broker acknowledgement withheld", topic, failure);
                }
            } catch (Exception acknowledgementFailure) {
                    log.warn("Unable to acknowledge MQTT message {} on {}; broker will redeliver",
                            message.getId(), topic, acknowledgementFailure);
            } finally {
                if (inFlight.decrementAndGet() == 0) {
                    synchronized (drainMonitor) { drainMonitor.notifyAll(); }
                }
            }
        });
    }

    private void handleCommandResult(String topic, MqttMessage message) {
        inFlight.incrementAndGet();
        try {
            commandResults.handle(topic, message.getPayload());
            if (message.getQos() > 0) client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (Exception failure) {
            log.warn("Command result persistence failed for {}; broker acknowledgement withheld", topic, failure);
        } finally {
            if (inFlight.decrementAndGet() == 0) {
                synchronized (drainMonitor) { drainMonitor.notifyAll(); }
            }
        }
    }

    private void handleShadowReport(String topic, MqttMessage message) {
        inFlight.incrementAndGet();
        var metadata = new DeviceShadowIngressService.UplinkMetadata(topic, message.getQos(), message.isRetained());
        shadows.ingest(message.getPayload(), metadata).whenComplete((outcome, failure) -> {
            try {
                if (failure == null) {
                    if (message.getQos() > 0) client.messageArrivedComplete(message.getId(), message.getQos());
                } else {
                    log.warn("Kafka shadow handoff failed for {}; broker acknowledgement withheld", topic, failure);
                }
            } catch (Exception acknowledgementFailure) {
                log.warn("Unable to acknowledge shadow report {} on {}; broker will redeliver",
                        message.getId(), topic, acknowledgementFailure);
            } finally {
                if (inFlight.decrementAndGet() == 0) {
                    synchronized (drainMonitor) { drainMonitor.notifyAll(); }
                }
            }
        });
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        if (reconnect && running.get()) {
            try {
                client.subscribe(new String[]{topicFilter, commandResultFilter, shadowReportedFilter},
                        new int[]{1, 1, 1});
            } catch (Exception subscribeFailure) {
                log.error("Unable to restore MQTT telemetry subscription", subscribeFailure);
            }
        }
    }

    @Override public void connectionLost(Throwable cause) { log.warn("MQTT connection lost", cause); }
    @Override public void deliveryComplete(IMqttDeliveryToken token) { }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            if (client.isConnected()) {
                client.unsubscribe(new String[]{topicFilter, commandResultFilter, shadowReportedFilter})
                        .waitForCompletion(10_000);
                awaitDrain(30_000);
                client.disconnect(30_000).waitForCompletion(30_000);
            }
            client.close();
        } catch (Exception shutdownFailure) {
            throw new IllegalStateException("Unable to stop MQTT client", shutdownFailure);
        }
    }

    private void awaitDrain(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (drainMonitor) {
            while (inFlight.get() > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    log.warn("Stopping MQTT ingress with {} Kafka handoffs still in flight", inFlight.get());
                    return;
                }
                drainMonitor.wait(remaining);
            }
        }
    }

    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return 0; }
}

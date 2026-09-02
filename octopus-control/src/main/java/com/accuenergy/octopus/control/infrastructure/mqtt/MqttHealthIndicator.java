package com.accuenergy.octopus.control.infrastructure.mqtt;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("mqtt")
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public final class MqttHealthIndicator implements HealthIndicator {
    private final MqttAsyncClient client;

    public MqttHealthIndicator(MqttAsyncClient client) { this.client = client; }

    @Override
    public Health health() {
        if (client.isConnected()) {
            return Health.up().withDetail("serverUri", client.getServerURI())
                    .withDetail("clientId", client.getClientId()).build();
        }
        return Health.down().withDetail("serverUri", client.getServerURI())
                .withDetail("clientId", client.getClientId()).build();
    }
}

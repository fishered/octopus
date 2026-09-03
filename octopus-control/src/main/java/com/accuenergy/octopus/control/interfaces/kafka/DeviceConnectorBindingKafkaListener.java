package com.accuenergy.octopus.control.interfaces.kafka;

import com.accuenergy.octopus.api.control.DeviceConnectorBindingChanged;
import com.accuenergy.octopus.control.infrastructure.redis.RedisDeviceBindingResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public final class DeviceConnectorBindingKafkaListener {
    private final ObjectMapper json; private final RedisDeviceBindingResolver bindings;
    public DeviceConnectorBindingKafkaListener(ObjectMapper json, RedisDeviceBindingResolver bindings) { this.json=json; this.bindings=bindings; }
    @KafkaListener(topics = "$"+"{octopus.kafka.device-connector-binding-topic:octopus.local.device.connector-binding.v1}",
            groupId = "$"+"{octopus.kafka.device-connector-binding-group:octopus-control-device-binding-v1}")
    public void consume(byte[] payload, @Header(KafkaHeaders.RECEIVED_KEY) String key, Acknowledgment acknowledgment) throws Exception {
        DeviceConnectorBindingChanged event=json.readValue(payload,DeviceConnectorBindingChanged.class);
        if (!event.orderingKey().equals(key)) throw new IllegalArgumentException("Connector binding Kafka key does not match ordering domain");
        bindings.apply(event); acknowledgment.acknowledge();
    }
}

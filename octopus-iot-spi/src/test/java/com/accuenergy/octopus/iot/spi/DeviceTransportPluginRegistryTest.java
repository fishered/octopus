package com.accuenergy.octopus.iot.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeviceTransportPluginRegistryTest {
    @Test
    void indexesPluginsAndRejectsDuplicates() {
        DeviceTransportPlugin first = plugin("mqtt-json");
        assertEquals(first, new DeviceTransportPluginRegistry(List.of(first)).require("mqtt-json"));
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceTransportPluginRegistry(List.of(first, plugin("mqtt-json"))));
    }

    @Test
    void routesToDeviceBindingAndRejectsUnsupportedKinds() {
        DeviceTransportPlugin plugin = plugin("mqtt-json");
        var tenant = UUID.randomUUID();
        var device = UUID.randomUUID();
        var router = new DeviceTransportRouter(new DeviceTransportPluginRegistry(List.of(plugin)),
                (t, d) -> java.util.Optional.of(new DeviceBinding(t, d, "mqtt-json", "json", 1)), "mqtt-json");
        var message = new OutboundMessage(MessageKind.COMMAND, tenant, device, UUID.randomUUID(), "switch",
                new byte[] {1}, Instant.now(), Instant.now().plusSeconds(30), "application/json");
        assertEquals(PublishReceipt.Status.ACCEPTED, router.publish(message).toCompletableFuture().join().status());
        var unsupported = new OutboundMessage(MessageKind.TELEMETRY, tenant, device, message.correlationId(),
                message.operation(), message.payload(), message.requestedAt(), message.expiresAt(), message.contentType());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.publish(unsupported).toCompletableFuture().join());
    }

    private static DeviceTransportPlugin plugin(String id) {
        return new DeviceTransportPlugin() {
            private final PluginDescriptor descriptor = new PluginDescriptor(id, "1.0.0", 1,
                    Set.of(MessageKind.COMMAND), "{}");
            @Override public PluginDescriptor descriptor() { return descriptor; }
            @Override public void start(InboundMessageHandler handler) { }
            @Override public java.util.concurrent.CompletionStage<PublishReceipt> publish(OutboundMessage message) {
                return CompletableFuture.completedFuture(new PublishReceipt(message.correlationId(),
                        PublishReceipt.Status.ACCEPTED, Instant.now(), null));
            }
            @Override public PluginHealth health() { return new PluginHealth(PluginHealth.Status.UP, Instant.now(), ""); }
            @Override public void stop() { }
        };
    }
}

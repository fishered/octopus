package com.accuenergy.octopus.iot.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        RecordingPlugin plugin = plugin("mqtt-json");
        var tenant = UUID.randomUUID();
        var device = UUID.randomUUID();
        byte[] encoded = new byte[] {9, 8};
        ProtocolCodec codec = new ProtocolCodec() {
            public String codecId() { return "json"; }
            public boolean supports(InboundMessage message, MessageKind kind) { return true; }
            public DecodedPayload decode(InboundMessage message, MessageKind kind) { return new DecodedPayload(kind, message.payload()); }
            public byte[] encode(OutboundMessage message) { return encoded; }
        };
        var router = new DeviceTransportRouter(new DeviceTransportPluginRegistry(List.of(plugin)),
                (t, d) -> java.util.Optional.of(new DeviceBinding(t, d, "mqtt-json", "json", 1)),
                new ProtocolCodecRegistry(List.of(codec)), "mqtt-json");
        var message = new OutboundMessage(MessageKind.COMMAND, tenant, device, UUID.randomUUID(), "switch",
                new byte[] {1}, Instant.now(), Instant.now().plusSeconds(30), "application/json");
        assertEquals(PublishReceipt.Status.ACCEPTED, router.publish(message).toCompletableFuture().join().status());
        assertArrayEquals(encoded, plugin.publishedPayload);
        var unsupported = new OutboundMessage(MessageKind.TELEMETRY, tenant, device, message.correlationId(),
                message.operation(), message.payload(), message.requestedAt(), message.expiresAt(), message.contentType());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.publish(unsupported).toCompletableFuture().join());
    }

    @Test
    void refusesMissingAndDisabledBindingsInsteadOfUsingDefaultPlugin() {
        DeviceTransportPlugin plugin = plugin("mqtt-json");
        var tenant = UUID.randomUUID();
        var device = UUID.randomUUID();
        var message = new OutboundMessage(MessageKind.COMMAND, tenant, device, UUID.randomUUID(), "switch",
                new byte[] {1}, Instant.now(), Instant.now().plusSeconds(30), "application/json");
        var registry = new DeviceTransportPluginRegistry(List.of(plugin));
        var missing = new DeviceTransportRouter(registry, (t, d) -> java.util.Optional.empty(), "mqtt-json");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> missing.publish(message).toCompletableFuture().join());
        var disabled = new DeviceTransportRouter(registry,
                (t, d) -> java.util.Optional.of(new DeviceBinding(t, d, "mqtt-json", "json",
                        DeviceBinding.Status.DISABLED, 1)), "mqtt-json");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> disabled.publish(message).toCompletableFuture().join());
    }

    private static RecordingPlugin plugin(String id) {
        return new RecordingPlugin(id);
    }

    private static final class RecordingPlugin implements DeviceTransportPlugin {
        private final PluginDescriptor descriptor;
        private byte[] publishedPayload;

        private RecordingPlugin(String id) {
            descriptor = new PluginDescriptor(id, "1.0.0", 1, Set.of(MessageKind.COMMAND), "{}");
        }

        @Override public PluginDescriptor descriptor() { return descriptor; }
        @Override public void start(InboundMessageHandler handler) { }
        @Override public java.util.concurrent.CompletionStage<PublishReceipt> publish(OutboundMessage message) {
            publishedPayload = message.payload();
            return CompletableFuture.completedFuture(new PublishReceipt(message.correlationId(),
                    PublishReceipt.Status.ACCEPTED, Instant.now(), null));
        }
        @Override public PluginHealth health() { return new PluginHealth(PluginHealth.Status.UP, Instant.now(), ""); }
        @Override public void stop() { }
    }
}

package com.accuenergy.octopus.iot.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeviceTransportPluginRuntimeTest {
    @Test
    void startsInIdOrderAndStopsInReverseOrder() {
        List<String> events = new ArrayList<>();
        var second = new TestPlugin("z-transport", events, false);
        var first = new TestPlugin("a-transport", events, false);
        var runtime = new DeviceTransportPluginRuntime(
                new DeviceTransportPluginRegistry(List.of(second, first)),
                message -> CompletableFuture.completedFuture(null));

        runtime.start();
        assertTrue(runtime.isRunning());
        runtime.stop();

        assertFalse(runtime.isRunning());
        assertEquals(List.of("start:a-transport", "start:z-transport",
                "stop:z-transport", "stop:a-transport"), events);
    }

    @Test
    void rollsBackStartedPluginsWhenStartupFails() {
        List<String> events = new ArrayList<>();
        var first = new TestPlugin("a-transport", events, false);
        var failing = new TestPlugin("b-transport", events, true);
        var runtime = new DeviceTransportPluginRuntime(
                new DeviceTransportPluginRegistry(List.of(first, failing)),
                message -> CompletableFuture.completedFuture(null));

        assertThrows(IllegalStateException.class, runtime::start);
        assertFalse(runtime.isRunning());
        assertEquals(List.of("start:a-transport", "start:b-transport",
                "stop:b-transport", "stop:a-transport"), events);
    }

    @Test
    void rejectsInboundMessagesClaimingAnotherPluginIdentity() {
        var plugin = new TestPlugin("mqtt-json", new ArrayList<>(), false);
        var runtime = new DeviceTransportPluginRuntime(
                new DeviceTransportPluginRegistry(List.of(plugin)),
                message -> CompletableFuture.completedFuture(null));
        runtime.start();

        InboundMessage message = new InboundMessage("other-plugin", "devices/1/telemetry",
                new byte[] {1}, 1, false, Instant.now(), "application/octet-stream");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> plugin.emit(message).toCompletableFuture().join());
    }

    private static final class TestPlugin implements DeviceTransportPlugin {
        private final PluginDescriptor descriptor;
        private final List<String> events;
        private final boolean failStart;
        private InboundMessageHandler handler;

        private TestPlugin(String id, List<String> events, boolean failStart) {
            this.descriptor = new PluginDescriptor(id, "1.0.0", PluginDescriptor.CURRENT_SPI_VERSION,
                    Set.of(MessageKind.TELEMETRY), Set.of("json"), "{}");
            this.events = events;
            this.failStart = failStart;
        }

        @Override public PluginDescriptor descriptor() { return descriptor; }

        @Override public void start(InboundMessageHandler handler) {
            events.add("start:" + descriptor.pluginId());
            if (failStart) throw new IllegalStateException("start failed");
            this.handler = handler;
        }

        java.util.concurrent.CompletionStage<Void> emit(InboundMessage message) {
            return handler.onMessage(message);
        }

        @Override public java.util.concurrent.CompletionStage<PublishReceipt> publish(OutboundMessage message) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override public PluginHealth health() {
            return new PluginHealth(PluginHealth.Status.UP, Instant.now(), "");
        }

        @Override public void stop() { events.add("stop:" + descriptor.pluginId()); }
    }
}

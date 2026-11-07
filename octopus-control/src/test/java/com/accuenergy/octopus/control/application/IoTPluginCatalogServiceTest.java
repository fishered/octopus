package com.accuenergy.octopus.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.iot.spi.DeviceTransportPlugin;
import com.accuenergy.octopus.iot.spi.DeviceTransportPluginRegistry;
import com.accuenergy.octopus.iot.spi.InboundMessageHandler;
import com.accuenergy.octopus.iot.spi.MessageKind;
import com.accuenergy.octopus.iot.spi.OutboundMessage;
import com.accuenergy.octopus.iot.spi.PluginDescriptor;
import com.accuenergy.octopus.iot.spi.PluginHealth;
import com.accuenergy.octopus.iot.spi.PublishReceipt;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class IoTPluginCatalogServiceTest {
    @Test
    void listsSanitizedDescriptorsAndAcceptsCompatibleBinding() {
        var service = service(PluginHealth.Status.UP);

        var entries = service.catalog();
        var result = service.preflight("vendor-edge", "vendor-json", Set.of(MessageKind.COMMAND));

        assertEquals(1, entries.size());
        assertEquals("{}", entries.getFirst().configurationSchema());
        assertTrue(result.compatible());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void rejectsUnsupportedCodecAndUnavailablePlugin() {
        var service = service(PluginHealth.Status.DOWN);

        var result = service.preflight("vendor-edge", "protobuf", Set.of(MessageKind.COMMAND_RESULT));

        assertFalse(result.compatible());
        assertTrue(result.errors().contains("CODEC_NOT_SUPPORTED"));
        assertTrue(result.errors().contains("MESSAGE_KIND_NOT_SUPPORTED"));
        assertTrue(result.errors().contains("PLUGIN_UNAVAILABLE"));
    }

    private static IoTPluginCatalogService service(PluginHealth.Status status) {
        DeviceTransportPlugin plugin = new DeviceTransportPlugin() {
            @Override public PluginDescriptor descriptor() {
                return new PluginDescriptor("vendor-edge", "1.2.0", PluginDescriptor.CURRENT_SPI_VERSION,
                        Set.of(MessageKind.COMMAND), Set.of("vendor-json"), "{}");
            }
            @Override public void start(InboundMessageHandler handler) { }
            @Override public java.util.concurrent.CompletionStage<PublishReceipt> publish(OutboundMessage message) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            @Override public PluginHealth health() { return new PluginHealth(status, Instant.now(), "secret detail"); }
            @Override public void stop() { }
        };
        return new IoTPluginCatalogService(new DeviceTransportPluginRegistry(List.of(plugin)));
    }
}

package com.accuenergy.octopus.control.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class IoTPluginHealthIndicatorTest {
    @Test
    void reportsDownWithoutLeakingExceptionDetails() {
        DeviceTransportPlugin failing = new TestPlugin() {
            @Override public PluginHealth health() { throw new IllegalStateException("secret-token"); }
        };
        var indicator = new IoTPluginHealthIndicator(new DeviceTransportPluginRegistry(List.of(failing)));

        var health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        String details = health.getDetails().toString();
        assertEquals(false, details.contains("secret-token"));
    }

    private static class TestPlugin implements DeviceTransportPlugin {
        @Override public PluginDescriptor descriptor() {
            return new PluginDescriptor("test-plugin", "1.0.0", PluginDescriptor.CURRENT_SPI_VERSION,
                    Set.of(MessageKind.COMMAND), "{}");
        }
        @Override public void start(InboundMessageHandler inboundHandler) { }
        @Override public java.util.concurrent.CompletionStage<PublishReceipt> publish(OutboundMessage message) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public PluginHealth health() {
            return new PluginHealth(PluginHealth.Status.UP, Instant.now(), "connected");
        }
        @Override public void stop() { }
    }
}

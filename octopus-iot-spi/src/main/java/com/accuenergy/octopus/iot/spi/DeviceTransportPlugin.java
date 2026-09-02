package com.accuenergy.octopus.iot.spi;

import java.util.concurrent.CompletionStage;

/** Transport boundary. Implementations must preserve correlation IDs and at-least-once semantics. */
public interface DeviceTransportPlugin extends AutoCloseable {
    PluginDescriptor descriptor();

    void start(InboundMessageHandler inboundHandler);

    CompletionStage<PublishReceipt> publish(OutboundMessage message);

    PluginHealth health();

    void stop();

    @Override
    default void close() { stop(); }
}

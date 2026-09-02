package com.accuenergy.octopus.iot.spi;

@FunctionalInterface
public interface InboundMessageHandler {
    void onMessage(InboundMessage message) throws Exception;
}

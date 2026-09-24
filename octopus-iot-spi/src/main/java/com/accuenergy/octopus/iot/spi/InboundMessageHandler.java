package com.accuenergy.octopus.iot.spi;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface InboundMessageHandler {
    CompletionStage<Void> onMessage(InboundMessage message);
}

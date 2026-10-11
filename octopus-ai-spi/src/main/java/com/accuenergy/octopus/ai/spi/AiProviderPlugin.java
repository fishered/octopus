package com.accuenergy.octopus.ai.spi;

import java.util.concurrent.CompletionStage;

/** Provider boundary. Implementations translate requests to a hosted or local model API. */
public interface AiProviderPlugin {
    AiProviderDescriptor descriptor();

    CompletionStage<AiResponse> generate(AiRequest request);
}

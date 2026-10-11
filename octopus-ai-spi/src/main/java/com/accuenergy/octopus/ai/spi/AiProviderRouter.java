package com.accuenergy.octopus.ai.spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Validates capability/model support before dispatching to a configured provider. */
public final class AiProviderRouter {
    private final AiProviderPluginRegistry registry;
    private final String defaultProviderId;

    public AiProviderRouter(AiProviderPluginRegistry registry, String defaultProviderId) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (defaultProviderId == null || defaultProviderId.isBlank()) {
            throw new IllegalArgumentException("defaultProviderId is required");
        }
        this.defaultProviderId = defaultProviderId.strip();
    }

    public CompletionStage<AiResponse> generate(AiRequest request) {
        Objects.requireNonNull(request, "request");
        AiProviderPlugin provider;
        try {
            provider = registry.require(defaultProviderId);
            AiProviderDescriptor descriptor = provider.descriptor();
            if (!descriptor.capabilities().contains(request.capability())) {
                throw new IllegalArgumentException("AI provider does not support capability " + request.capability());
            }
            if (!descriptor.models().contains(request.model())) {
                throw new IllegalArgumentException("AI provider does not support model " + request.model());
            }
        } catch (RuntimeException invalidRoute) {
            return CompletableFuture.failedFuture(invalidRoute);
        }
        try {
            CompletionStage<AiResponse> response = provider.generate(request);
            return Objects.requireNonNull(response, "AI provider returned null completion stage")
                    .thenApply(result -> validateResponse(request, provider, result));
        } catch (RuntimeException providerFailure) {
            return CompletableFuture.failedFuture(providerFailure);
        }
    }

    private static AiResponse validateResponse(AiRequest request, AiProviderPlugin provider, AiResponse response) {
        Objects.requireNonNull(response, "AI provider returned null response");
        if (!request.correlationId().equals(response.correlationId())) {
            throw new IllegalStateException("AI response correlation ID does not match request");
        }
        if (!provider.descriptor().providerId().equals(response.providerId())) {
            throw new IllegalStateException("AI response provider ID does not match selected provider");
        }
        if (!request.model().equals(response.model())) {
            throw new IllegalStateException("AI response model does not match request");
        }
        return response;
    }
}

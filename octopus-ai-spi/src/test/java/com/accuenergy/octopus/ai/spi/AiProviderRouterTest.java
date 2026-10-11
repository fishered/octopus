package com.accuenergy.octopus.ai.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AiProviderRouterTest {
    @Test
    void routesOnlySupportedCapabilitiesAndModels() {
        var provider = new TestProvider();
        var router = new AiProviderRouter(new AiProviderPluginRegistry(List.of(provider)), "local-ai");
        AiRequest request = request(AiCapability.DEVICE_INTEGRATION, "test-model");

        assertEquals(request.correlationId(), router.generate(request).toCompletableFuture().join().correlationId());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.generate(request(AiCapability.OPERATIONS_ASSISTANT, "test-model"))
                        .toCompletableFuture().join());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.generate(request(AiCapability.DEVICE_INTEGRATION, "missing-model"))
                        .toCompletableFuture().join());
    }

    @Test
    void rejectsProviderResponsesWithAnotherCorrelationId() {
        AiProviderPlugin provider = new TestProvider() {
            @Override public java.util.concurrent.CompletionStage<AiResponse> generate(AiRequest request) {
                return CompletableFuture.completedFuture(response(UUID.randomUUID(), request.model()));
            }
        };
        var router = new AiProviderRouter(new AiProviderPluginRegistry(List.of(provider)), "local-ai");

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.generate(request(AiCapability.DEVICE_INTEGRATION, "test-model"))
                        .toCompletableFuture().join());
    }

    @Test
    void rejectsProviderResponsesFromAnotherModel() {
        AiProviderPlugin provider = new TestProvider() {
            @Override public java.util.concurrent.CompletionStage<AiResponse> generate(AiRequest request) {
                return CompletableFuture.completedFuture(response(request.correlationId(), "other-model"));
            }
        };
        var router = new AiProviderRouter(new AiProviderPluginRegistry(List.of(provider)), "local-ai");

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> router.generate(request(AiCapability.DEVICE_INTEGRATION, "test-model"))
                        .toCompletableFuture().join());
    }

    @Test
    void rejectsDuplicateAndIncompatibleProviders() {
        var provider = new TestProvider();
        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderPluginRegistry(List.of(provider, provider)));
        AiProviderPlugin future = new TestProvider() {
            @Override public AiProviderDescriptor descriptor() {
                return new AiProviderDescriptor("future-ai", "1.0.0",
                        AiProviderDescriptor.CURRENT_SPI_VERSION + 1,
                        Set.of(AiCapability.DEVICE_INTEGRATION), Set.of("test-model"), "{}");
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderPluginRegistry(List.of(future)));
    }

    private static AiRequest request(AiCapability capability, String model) {
        return new AiRequest(UUID.randomUUID(), UUID.randomUUID(), capability, model,
                "Return a proposal", "{}", "{}", Map.of());
    }

    private static AiResponse response(UUID correlationId, String model) {
        return new AiResponse(correlationId, "local-ai", model, "{}", AiResponse.FinishReason.COMPLETED,
                new AiResponse.TokenUsage(10, 5), Instant.now());
    }

    private static class TestProvider implements AiProviderPlugin {
        @Override public AiProviderDescriptor descriptor() {
            return new AiProviderDescriptor("local-ai", "1.0.0", AiProviderDescriptor.CURRENT_SPI_VERSION,
                    Set.of(AiCapability.DEVICE_INTEGRATION), Set.of("test-model"), "{}");
        }

        @Override public java.util.concurrent.CompletionStage<AiResponse> generate(AiRequest request) {
            return CompletableFuture.completedFuture(response(request.correlationId(), request.model()));
        }
    }
}

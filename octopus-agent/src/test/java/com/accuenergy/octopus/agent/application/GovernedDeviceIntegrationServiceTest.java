package com.accuenergy.octopus.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.accuenergy.octopus.ai.spi.AiCapability;
import com.accuenergy.octopus.ai.spi.AiProviderDescriptor;
import com.accuenergy.octopus.ai.spi.AiProviderPlugin;
import com.accuenergy.octopus.ai.spi.AiProviderPluginRegistry;
import com.accuenergy.octopus.ai.spi.AiProviderRouter;
import com.accuenergy.octopus.ai.spi.AiRequest;
import com.accuenergy.octopus.ai.spi.AiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GovernedDeviceIntegrationServiceTest {
    @Test
    void redactsBeforeDispatchAndRecordsMetadataOnlyAudit() {
        AtomicReference<AiRequest> providerRequest = new AtomicReference<>();
        AiProviderPlugin provider = new AiProviderPlugin() {
            @Override public AiProviderDescriptor descriptor() {
                return new AiProviderDescriptor("test-ai", "1", AiProviderDescriptor.CURRENT_SPI_VERSION,
                        Set.of(AiCapability.DEVICE_INTEGRATION), Set.of("model"), "{}");
            }
            @Override public java.util.concurrent.CompletionStage<AiResponse> generate(AiRequest request) {
                providerRequest.set(request);
                String proposal = "{\"pluginId\":\"mqtt-json\",\"codecId\":\"json\",\"thingModel\":{},"
                        + "\"mapping\":{},\"checks\":[\"review\"]}";
                return CompletableFuture.completedFuture(new AiResponse(request.correlationId(), "test-ai", "model",
                        proposal, AiResponse.FinishReason.COMPLETED, new AiResponse.TokenUsage(12, 8),
                        Instant.parse("2026-10-11T00:00:01Z")));
            }
        };
        ObjectMapper json = new ObjectMapper();
        var assistant = new DeviceIntegrationAssistant(
                new AiProviderRouter(new AiProviderPluginRegistry(List.of(provider)), "test-ai"), json, "model");
        AtomicReference<AiAuditSink.AiAuditEvent> audit = new AtomicReference<>();
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
        var service = new GovernedDeviceIntegrationService(assistant, new SensitiveDataRedactor(json),
                new TenantAiBudget(clock, 2, 1000), audit::set, clock);
        UUID tenantId = UUID.randomUUID();

        service.draft(new GovernedDeviceIntegrationService.DraftCommand(tenantId, "actor-1", UUID.randomUUID(),
                "Acme", "MQTT", "{\"token\":\"do-not-send\",\"value\":1}",
                "password=also-secret", List.of("mqtt-json"))).toCompletableFuture().join();

        assertFalse(providerRequest.get().input().contains("do-not-send"));
        assertFalse(providerRequest.get().input().contains("also-secret"));
        assertEquals("COMPLETED", audit.get().outcome());
        assertEquals(12, audit.get().inputTokens());
        assertEquals(tenantId, audit.get().tenantId());
    }
}

package com.accuenergy.octopus.agent.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accuenergy.octopus.ai.spi.AiCapability;
import com.accuenergy.octopus.ai.spi.AiRequest;
import com.accuenergy.octopus.ai.spi.AiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleProviderPluginTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC);
    private final OpenAiCompatibleProviderPlugin provider = new OpenAiCompatibleProviderPlugin(
            RestClient.builder().baseUrl("https://example.invalid/v1")
                    .requestFactory(new SimpleClientHttpRequestFactory()).build(),
            json, Runnable::run, clock,
            "openai-compatible", Set.of("integration-model"), "/chat/completions");

    @Test
    void translatesProviderNeutralRequestToStructuredChatCompletion() {
        AiRequest request = request();

        var body = OpenAiCompatibleProviderPlugin.toProviderRequest(json, request);

        assertEquals("integration-model", body.path("model").asText());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("json_schema", body.path("response_format").path("type").asText());
        assertTrue(body.path("response_format").path("json_schema").path("strict").asBoolean());
        assertEquals("object", body.path("response_format").path("json_schema")
                .path("schema").path("type").asText());
    }

    @Test
    void mapsContentFinishReasonAndTokenUsageWithoutChangingCorrelation() throws Exception {
        AiRequest request = request();
        var body = json.readTree("""
                {"model":"integration-model","choices":[{"finish_reason":"stop","message":{
                 "content":"{\\"pluginId\\":\\"mqtt-json\\"}"}}],
                 "usage":{"prompt_tokens":21,"completion_tokens":8}}
                """);

        AiResponse response = provider.parseResponse(request, body);

        assertEquals(request.correlationId(), response.correlationId());
        assertEquals(AiResponse.FinishReason.COMPLETED, response.finishReason());
        assertEquals(new AiResponse.TokenUsage(21, 8), response.usage());
        assertEquals(clock.instant(), response.completedAt());
    }

    private static AiRequest request() {
        return new AiRequest(UUID.randomUUID(), UUID.randomUUID(), AiCapability.DEVICE_INTEGRATION,
                "integration-model", "Return JSON", "{\"protocol\":\"MQTT\"}",
                "{\"type\":\"object\"}", Map.of());
    }
}

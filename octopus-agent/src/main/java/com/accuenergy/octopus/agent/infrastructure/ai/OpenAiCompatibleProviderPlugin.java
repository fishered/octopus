package com.accuenergy.octopus.agent.infrastructure.ai;

import com.accuenergy.octopus.ai.spi.AiCapability;
import com.accuenergy.octopus.ai.spi.AiProviderDescriptor;
import com.accuenergy.octopus.ai.spi.AiProviderPlugin;
import com.accuenergy.octopus.ai.spi.AiRequest;
import com.accuenergy.octopus.ai.spi.AiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Hosted or local provider using the widely supported Chat Completions HTTP contract. */
public final class OpenAiCompatibleProviderPlugin implements AiProviderPlugin {
    private static final String CONFIGURATION_SCHEMA = """
            {"type":"object","required":["baseUrl","models"],"properties":{
              "baseUrl":{"type":"string","format":"uri"},
              "apiKey":{"type":"string","writeOnly":true},
              "models":{"type":"array","items":{"type":"string"},"minItems":1}},
             "additionalProperties":false}
            """;

    private final RestClient client;
    private final ObjectMapper json;
    private final Executor executor;
    private final Clock clock;
    private final String chatCompletionsPath;
    private final AiProviderDescriptor descriptor;

    public OpenAiCompatibleProviderPlugin(RestClient client, ObjectMapper json, Executor executor,
            Clock clock, String providerId, Set<String> models, String chatCompletionsPath) {
        this.client = Objects.requireNonNull(client, "client");
        this.json = Objects.requireNonNull(json, "json");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.chatCompletionsPath = requirePath(chatCompletionsPath);
        this.descriptor = new AiProviderDescriptor(providerId, "1.0.0",
                AiProviderDescriptor.CURRENT_SPI_VERSION, Set.of(AiCapability.values()), models,
                CONFIGURATION_SCHEMA);
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public CompletionStage<AiResponse> generate(AiRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> invoke(request), executor);
    }

    private AiResponse invoke(AiRequest request) {
        try {
            JsonNode body = client.post().uri(chatCompletionsPath)
                    .header("X-Request-ID", request.correlationId().toString())
                    .body(toProviderRequest(json, request))
                    .retrieve().body(JsonNode.class);
            if (body == null) throw new IllegalStateException("AI provider returned an empty response");
            return parseResponse(request, body);
        } catch (RestClientException providerFailure) {
            // Provider bodies may contain prompt fragments; keep failures deliberately sanitized.
            throw new IllegalStateException("AI provider request failed");
        }
    }

    static ObjectNode toProviderRequest(ObjectMapper json, AiRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", request.model());
        body.put("temperature", 0.1);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.instructions());
        messages.addObject().put("role", "user").put("content", request.input());
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode schema = responseFormat.putObject("json_schema");
        schema.put("name", "octopus_" + request.capability().name().toLowerCase(java.util.Locale.ROOT));
        schema.put("strict", true);
        try {
            schema.set("schema", json.readTree(request.outputSchema()));
        } catch (JsonProcessingException invalidSchema) {
            throw new IllegalArgumentException("AI output schema is invalid JSON", invalidSchema);
        }
        return body;
    }

    AiResponse parseResponse(AiRequest request, JsonNode body) {
        JsonNode choice = body.path("choices").path(0);
        String output = choice.path("message").path("content").asText("");
        if (output.isBlank()) throw new IllegalStateException("AI provider returned no message content");
        JsonNode usage = body.path("usage");
        long inputTokens = nonNegative(usage.path("prompt_tokens").asLong(0));
        long outputTokens = nonNegative(usage.path("completion_tokens").asLong(0));
        return new AiResponse(request.correlationId(), descriptor.providerId(), request.model(), output,
                finishReason(choice.path("finish_reason").asText("")),
                new AiResponse.TokenUsage(inputTokens, outputTokens), clock.instant());
    }

    private static AiResponse.FinishReason finishReason(String value) {
        return switch (value) {
            case "stop" -> AiResponse.FinishReason.COMPLETED;
            case "length" -> AiResponse.FinishReason.LENGTH;
            case "content_filter" -> AiResponse.FinishReason.CONTENT_FILTERED;
            case "tool_calls", "function_call" -> AiResponse.FinishReason.TOOL_REQUIRED;
            default -> AiResponse.FinishReason.FAILED;
        };
    }

    private static long nonNegative(long value) {
        if (value < 0) throw new IllegalStateException("AI provider returned invalid token usage");
        return value;
    }

    private static String requirePath(String value) {
        if (value == null || value.isBlank() || !value.startsWith("/") || value.contains("..")) {
            throw new IllegalArgumentException("chatCompletionsPath must be an absolute safe path");
        }
        return value;
    }
}

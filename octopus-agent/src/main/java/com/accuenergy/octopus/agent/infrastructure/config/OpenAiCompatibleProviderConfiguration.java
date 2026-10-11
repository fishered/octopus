package com.accuenergy.octopus.agent.infrastructure.config;

import com.accuenergy.octopus.ai.spi.AiProviderPlugin;
import com.accuenergy.octopus.agent.infrastructure.ai.OpenAiCompatibleProviderPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "octopus.ai.openai-compatible", name = "enabled", havingValue = "true")
public class OpenAiCompatibleProviderConfiguration {
    @Bean(destroyMethod = "close")
    ExecutorService aiProviderExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    AiProviderPlugin openAiCompatibleProvider(ObjectMapper json, ExecutorService aiProviderExecutor,
            @Value("${octopus.ai.openai-compatible.provider-id:openai-compatible}") String providerId,
            @Value("${octopus.ai.openai-compatible.base-url}") String baseUrl,
            @Value("${octopus.ai.openai-compatible.api-key:}") String apiKey,
            @Value("${octopus.ai.openai-compatible.models}") String models,
            @Value("${octopus.ai.openai-compatible.chat-completions-path:/chat/completions}") String path,
            @Value("${octopus.ai.openai-compatible.timeout:PT30S}") Duration timeout) {
        URI endpoint = validateEndpoint(baseUrl);
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("AI provider timeout must be between 1 ms and 5 minutes");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder client = RestClient.builder().baseUrl(endpoint.toString()).requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) {
            client.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip());
        }
        return new OpenAiCompatibleProviderPlugin(client.build(), json, aiProviderExecutor,
                java.time.Clock.systemUTC(),
                providerId, parseModels(models), path);
    }

    static URI validateEndpoint(String value) {
        URI endpoint;
        try {
            endpoint = URI.create(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("AI provider base URL must be an absolute HTTP(S) URI", invalid);
        }
        if (!endpoint.isAbsolute() || endpoint.getHost() == null
                || !("https".equalsIgnoreCase(endpoint.getScheme())
                || "http".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("AI provider base URL must be an absolute HTTP(S) URI");
        }
        if (endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("AI provider base URL must not contain credentials, query, or fragment");
        }
        return endpoint;
    }

    static Set<String> parseModels(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("AI provider models are required");
        Set<String> parsed = Arrays.stream(value.split(",")).map(String::strip).filter(model -> !model.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (parsed.isEmpty()) throw new IllegalArgumentException("AI provider models are required");
        return parsed;
    }
}

package com.accuenergy.octopus.agent.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderConfigurationTest {
    @Test
    void acceptsHostedAndLocalEndpointsButRejectsEmbeddedCredentials() {
        assertEquals("https", OpenAiCompatibleProviderConfiguration
                .validateEndpoint("https://api.example.com/v1").getScheme());
        assertEquals("http", OpenAiCompatibleProviderConfiguration
                .validateEndpoint("http://localhost:11434/v1").getScheme());
        assertThrows(IllegalArgumentException.class, () -> OpenAiCompatibleProviderConfiguration
                .validateEndpoint("https://secret@example.com/v1"));
    }

    @Test
    void parsesAnExplicitModelAllowList() {
        assertEquals(Set.of("model-a", "model-b"),
                OpenAiCompatibleProviderConfiguration.parseModels("model-a, model-b"));
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiCompatibleProviderConfiguration.parseModels("  "));
    }
}

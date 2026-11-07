package com.accuenergy.octopus.agent.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {
    private final SensitiveDataRedactor redactor = new SensitiveDataRedactor(new ObjectMapper());

    @Test
    void redactsNestedJsonCredentialsWithoutRemovingShape() {
        String result = redactor.redact("{\"device\":{\"token\":\"abc123\",\"value\":42},"
                + "\"items\":[{\"clientSecret\":\"hidden\"}]}");

        assertFalse(result.contains("abc123"));
        assertFalse(result.contains("hidden"));
        assertTrue(result.contains("[REDACTED]"));
        assertTrue(result.contains("\"value\":42"));
    }

    @Test
    void redactsSecretsInNonJsonProtocolFacts() {
        String result = redactor.redact("host=edge password=top-secret; token:abc123");

        assertFalse(result.contains("top-secret"));
        assertFalse(result.contains("abc123"));
        assertTrue(result.contains("password=[REDACTED]"));
    }
}

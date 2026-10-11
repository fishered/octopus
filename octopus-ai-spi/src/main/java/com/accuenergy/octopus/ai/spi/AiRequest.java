package com.accuenergy.octopus.ai.spi;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral, tenant-scoped request for structured advisory output. */
public record AiRequest(UUID tenantId, UUID correlationId, AiCapability capability, String model,
        String instructions, String input, String outputSchema, Map<String, String> metadata) {
    public AiRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(capability, "capability");
        model = requireText(model, "model", 128);
        instructions = requireText(instructions, "instructions", 16_384);
        input = requireText(input, "input", 262_144);
        outputSchema = requireText(outputSchema, "outputSchema", 65_536);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (metadata.size() > 32) throw new IllegalArgumentException("metadata has too many entries");
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.strip();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }
}

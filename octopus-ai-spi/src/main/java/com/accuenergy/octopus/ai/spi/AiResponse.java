package com.accuenergy.octopus.ai.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Structured provider result. It is advisory and must be validated before any platform mutation. */
public record AiResponse(UUID correlationId, String providerId, String model, String output,
        FinishReason finishReason, TokenUsage usage, Instant completedAt) {
    public AiResponse {
        Objects.requireNonNull(correlationId, "correlationId");
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        if (output == null || output.isBlank()) throw new IllegalArgumentException("output is required");
        Objects.requireNonNull(finishReason, "finishReason");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public enum FinishReason { COMPLETED, LENGTH, CONTENT_FILTERED, TOOL_REQUIRED, FAILED }

    public record TokenUsage(long inputTokens, long outputTokens) {
        public TokenUsage {
            if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("token usage cannot be negative");
        }
    }
}

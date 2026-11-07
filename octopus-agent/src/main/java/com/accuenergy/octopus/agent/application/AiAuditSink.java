package com.accuenergy.octopus.agent.application;

import java.time.Instant;
import java.util.UUID;

/** Audit boundary. Implementations must never persist prompts, payloads, credentials, or model output. */
public interface AiAuditSink {
    void record(AiAuditEvent event);

    record AiAuditEvent(UUID tenantId, String actorId, UUID correlationId, String workflow,
            String outcome, String providerId, String model, long inputTokens, long outputTokens,
            long durationMillis, Instant completedAt) { }
}

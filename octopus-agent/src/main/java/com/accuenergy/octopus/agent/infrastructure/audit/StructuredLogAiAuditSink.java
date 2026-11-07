package com.accuenergy.octopus.agent.infrastructure.audit;

import com.accuenergy.octopus.agent.application.AiAuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits metadata-only events for collection by the platform's append-only audit pipeline. */
@Component
public final class StructuredLogAiAuditSink implements AiAuditSink {
    private static final Logger AUDIT = LoggerFactory.getLogger("octopus.ai.audit");

    @Override
    public void record(AiAuditEvent event) {
        AUDIT.info("ai_audit tenantId={} actorId={} correlationId={} workflow={} outcome={} providerId={} "
                        + "model={} inputTokens={} outputTokens={} durationMillis={} completedAt={}",
                event.tenantId(), event.actorId(), event.correlationId(), event.workflow(), event.outcome(),
                event.providerId(), event.model(), event.inputTokens(), event.outputTokens(),
                event.durationMillis(), event.completedAt());
    }
}

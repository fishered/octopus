package com.accuenergy.octopus.agent.application;

import com.accuenergy.octopus.ai.spi.AiResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;

/** Adds redaction, tenant quotas, and payload-free audit evidence around the advisory workflow. */
@Service
public final class GovernedDeviceIntegrationService {
    private final DeviceIntegrationAssistant assistant;
    private final SensitiveDataRedactor redactor;
    private final TenantAiBudget budget;
    private final AiAuditSink audit;
    private final Clock clock;

    public GovernedDeviceIntegrationService(DeviceIntegrationAssistant assistant, SensitiveDataRedactor redactor,
            TenantAiBudget budget, AiAuditSink audit, Clock clock) {
        this.assistant = assistant;
        this.redactor = redactor;
        this.budget = budget;
        this.audit = audit;
        this.clock = clock;
    }

    public CompletionStage<AiResponse> draft(DraftCommand command) {
        Objects.requireNonNull(command, "command");
        String sample = redactor.redact(command.samplePayload());
        String requirements = redactor.redact(command.requirements());
        int estimatedTokens = estimateTokens(command.vendor(), command.protocol(), sample, requirements,
                String.join(",", command.installedPlugins()));
        budget.reserve(command.tenantId(), estimatedTokens);
        Instant startedAt = clock.instant();
        return assistant.draft(new DeviceIntegrationAssistant.DraftCommand(command.tenantId(), command.correlationId(),
                command.vendor(), command.protocol(), sample, requirements, command.installedPlugins()))
                .handle((response, failure) -> {
                    Instant completedAt = clock.instant();
                    if (failure != null) {
                        audit.record(new AiAuditSink.AiAuditEvent(command.tenantId(), command.actorId(),
                                command.correlationId(), "device-integration-draft", "FAILED", "", "", 0, 0,
                                Duration.between(startedAt, completedAt).toMillis(), completedAt));
                        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                        if (cause instanceof RuntimeException runtime) throw runtime;
                        throw new CompletionException(cause);
                    }
                    audit.record(new AiAuditSink.AiAuditEvent(command.tenantId(), command.actorId(),
                            command.correlationId(), "device-integration-draft", "COMPLETED",
                            response.providerId(), response.model(), response.usage().inputTokens(),
                            response.usage().outputTokens(), Duration.between(startedAt, completedAt).toMillis(),
                            completedAt));
                    return response;
                });
    }

    static int estimateTokens(String... values) {
        long characters = 0;
        for (String value : values) characters += value == null ? 0 : value.length();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (characters + 3) / 4));
    }

    public record DraftCommand(UUID tenantId, String actorId, UUID correlationId, String vendor, String protocol,
            String samplePayload, String requirements, List<String> installedPlugins) {
        public DraftCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            if (actorId == null || !actorId.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")) {
                throw new IllegalArgumentException("actorId is invalid");
            }
            Objects.requireNonNull(correlationId, "correlationId");
            installedPlugins = installedPlugins == null ? List.of() : List.copyOf(installedPlugins);
        }
    }
}

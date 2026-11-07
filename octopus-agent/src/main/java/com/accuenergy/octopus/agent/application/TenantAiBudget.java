package com.accuenergy.octopus.agent.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Conservative per-instance guard; production ingress may add a distributed quota above it. */
@Component
public final class TenantAiBudget {
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximumRequests;
    private final int maximumEstimatedTokens;

    public TenantAiBudget(Clock clock,
            @Value("${octopus.ai.governance.requests-per-minute:10}") int maximumRequests,
            @Value("${octopus.ai.governance.estimated-tokens-per-minute:20000}") int maximumEstimatedTokens) {
        if (maximumRequests < 1 || maximumEstimatedTokens < 1) {
            throw new IllegalArgumentException("AI governance budgets must be positive");
        }
        this.clock = clock;
        this.maximumRequests = maximumRequests;
        this.maximumEstimatedTokens = maximumEstimatedTokens;
    }

    public BudgetSnapshot reserve(UUID tenantId, int estimatedTokens) {
        if (estimatedTokens < 1) throw new IllegalArgumentException("estimatedTokens must be positive");
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Window updated = windows.compute(tenantId, (ignored, current) -> {
            Window base = current == null || !current.minute().equals(minute)
                    ? new Window(minute, 0, 0) : current;
            if (base.requests() + 1 > maximumRequests
                    || base.estimatedTokens() + estimatedTokens > maximumEstimatedTokens) {
                throw new AiBudgetExceededException();
            }
            return new Window(minute, base.requests() + 1, base.estimatedTokens() + estimatedTokens);
        });
        return new BudgetSnapshot(updated.requests(), maximumRequests,
                updated.estimatedTokens(), maximumEstimatedTokens, minute.plus(1, ChronoUnit.MINUTES));
    }

    private record Window(Instant minute, int requests, int estimatedTokens) { }

    public record BudgetSnapshot(int requestsUsed, int requestLimit, int estimatedTokensUsed,
            int estimatedTokenLimit, Instant resetsAt) { }
}

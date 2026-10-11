package com.accuenergy.octopus.mgmt.application.asset;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable idempotency boundary for the atomic onboarding use case. */
public interface DeviceOnboardingRequestRepository {
    Optional<RequestRecord> findForUpdate(UUID requestedBy, String idempotencyKey);

    boolean insertClaim(UUID requestId, UUID tenantId, UUID requestedBy, String idempotencyKey,
            String requestSha256, Instant requestedAt);

    void complete(UUID requestId, DeviceOnboardingUseCase.OnboardingResult result, Instant completedAt);

    record RequestRecord(UUID requestId, String requestSha256,
            Optional<DeviceOnboardingUseCase.OnboardingResult> result) { }
}

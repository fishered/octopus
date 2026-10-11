package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceOnboardingRequestRepository;
import com.accuenergy.octopus.mgmt.application.asset.DeviceOnboardingUseCase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public final class MybatisDeviceOnboardingRequestRepository implements DeviceOnboardingRequestRepository {
    private final DeviceOnboardingRequestMapper mapper;

    public MybatisDeviceOnboardingRequestRepository(DeviceOnboardingRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RequestRecord> findForUpdate(UUID requestedBy, String idempotencyKey) {
        return Optional.ofNullable(mapper.findForUpdate(requestedBy, idempotencyKey)).map(this::toRecord);
    }

    @Override
    public boolean insertClaim(UUID requestId, UUID tenantId, UUID requestedBy, String idempotencyKey,
            String requestSha256, Instant requestedAt) {
        return mapper.insertClaim(requestId, tenantId, requestedBy, idempotencyKey,
                requestSha256, requestedAt) == 1;
    }

    @Override
    public void complete(UUID requestId, DeviceOnboardingUseCase.OnboardingResult result, Instant completedAt) {
        if (mapper.complete(requestId, result.deviceId(), result.actualId(), result.code(),
                result.hardwareSerial(), result.pluginId(), result.codecId(), result.bindingVersion(),
                result.createdAt(), completedAt) != 1) {
            throw new IllegalStateException("Unable to complete onboarding idempotency record");
        }
    }

    private RequestRecord toRecord(DeviceOnboardingRequestMapper.RequestRow row) {
        Optional<DeviceOnboardingUseCase.OnboardingResult> result = Optional.empty();
        if ("COMPLETED".equals(row.status())) {
            result = Optional.of(new DeviceOnboardingUseCase.OnboardingResult(row.deviceId(), row.actualId(),
                    row.code(), row.hardwareSerial(), row.pluginId(), row.codecId(),
                    row.bindingVersion(), row.deviceCreatedAt()));
        } else if (!"PROCESSING".equals(row.status())) {
            throw new IllegalStateException("Unknown onboarding idempotency status: " + row.status());
        }
        return new RequestRecord(row.requestId(), row.requestSha256(), result);
    }
}

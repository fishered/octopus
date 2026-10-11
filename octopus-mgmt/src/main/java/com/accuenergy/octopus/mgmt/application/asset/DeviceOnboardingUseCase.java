package com.accuenergy.octopus.mgmt.application.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/** Atomic management boundary for the common device onboarding path. */
public interface DeviceOnboardingUseCase {
    OnboardingResult onboard(AuthenticatedPrincipal principal, OnboardDevice command);

    record OnboardDevice(String idempotencyKey, UUID organizationId, UUID facilityId, UUID deviceTypeId,
            UUID thingModelId, long modelVersion, String code, String displayName,
            String hardwareSerial, String manufacturer, String firmwareVersion, UUID certificateId,
            String pluginId, String codecId, String configRef) { }

    record OnboardingResult(UUID deviceId, UUID actualId, String code, String hardwareSerial,
            String pluginId, String codecId, long bindingVersion, Instant createdAt) { }
}

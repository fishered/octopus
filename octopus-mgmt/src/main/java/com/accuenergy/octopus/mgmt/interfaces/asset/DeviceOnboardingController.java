package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.common.security.AuthenticatedPrincipal;
import com.accuenergy.octopus.mgmt.application.asset.DeviceOnboardingUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** One-call happy path for a device whose type and thing model already exist. */
@RestController
@RequestMapping("/api/v1/device-onboardings")
public final class DeviceOnboardingController {
    private final DeviceOnboardingUseCase onboarding;

    public DeviceOnboardingController(DeviceOnboardingUseCase onboarding) {
        this.onboarding = onboarding;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("(hasAuthority('PERM_device:create') and hasAuthority('PERM_device:configure'))"
            + " or hasAuthority('PERM_platform:all')")
    public OnboardingResponse onboard(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnboardingRequest request) {
        var result = onboarding.onboard(principal(authentication), new DeviceOnboardingUseCase.OnboardDevice(
                idempotencyKey, request.organizationId(), request.facilityId(), request.deviceTypeId(), request.thingModelId(),
                request.modelVersion(), request.code(), request.displayName(), request.hardwareSerial(),
                request.manufacturer(), request.firmwareVersion(), request.certificateId(), request.pluginId(),
                request.codecId(), request.configRef()));
        return new OnboardingResponse(result.deviceId(), result.actualId(), result.code(),
                result.hardwareSerial(), result.pluginId(), result.codecId(),
                result.bindingVersion(), result.createdAt());
    }

    private static AuthenticatedPrincipal principal(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthenticatedPrincipal principal) return principal;
        throw new IllegalStateException("Authenticated principal is unavailable");
    }

    public record OnboardingRequest(@NotNull UUID organizationId, UUID facilityId,
            @NotNull UUID deviceTypeId, @NotNull UUID thingModelId, @Positive long modelVersion,
            @NotBlank String code, @NotBlank String displayName,
            @NotBlank String hardwareSerial, String manufacturer, String firmwareVersion, UUID certificateId,
            @NotBlank String pluginId, @NotBlank String codecId, String configRef) { }

    public record OnboardingResponse(UUID deviceId, UUID actualId, String code, String hardwareSerial,
            String pluginId, String codecId, long bindingVersion, Instant createdAt) { }
}

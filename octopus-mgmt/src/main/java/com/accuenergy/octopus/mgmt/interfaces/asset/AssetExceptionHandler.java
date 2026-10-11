package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceActualAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.asset.DeviceConnectorBindingAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.asset.DeviceAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.asset.DeviceOnboardingIdempotencyConflictException;
import com.accuenergy.octopus.mgmt.application.asset.FacilityAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {FacilityController.class, DeviceActualController.class,
        DeviceConnectorBindingController.class, DeviceOnboardingController.class})
public final class AssetExceptionHandler {
    @ExceptionHandler({FacilityAccessDeniedException.class, DeviceAccessDeniedException.class,
            DeviceActualAccessDeniedException.class, DeviceConnectorBindingAccessDeniedException.class})
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ASSET_ACCESS_DENIED"));
    }

    @ExceptionHandler(DeviceOnboardingIdempotencyConflictException.class)
    ResponseEntity<Map<String, String>> conflict(DeviceOnboardingIdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DEVICE_ONBOARDING_IDEMPOTENCY_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ASSET_REQUEST",
                "message", exception.getMessage()));
    }
}

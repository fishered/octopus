package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceActualAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.asset.DeviceConnectorBindingAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.asset.FacilityAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {FacilityController.class, DeviceActualController.class, DeviceConnectorBindingController.class})
public final class AssetExceptionHandler {
    @ExceptionHandler({FacilityAccessDeniedException.class, DeviceActualAccessDeniedException.class, DeviceConnectorBindingAccessDeniedException.class})
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ASSET_ACCESS_DENIED"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ASSET_REQUEST",
                "message", exception.getMessage()));
    }
}

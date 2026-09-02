package com.accuenergy.octopus.mgmt.interfaces.asset;

import com.accuenergy.octopus.mgmt.application.asset.DeviceAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeviceController.class)
public final class DeviceExceptionHandler {
    @ExceptionHandler(DeviceAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "DEVICE_ACCESS_DENIED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_DEVICE_REQUEST", "message", exception.getMessage()));
    }
}

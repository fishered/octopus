package com.accuenergy.octopus.mgmt.interfaces.control;

import com.accuenergy.octopus.mgmt.application.control.DeviceCommandAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.control.IdempotencyConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeviceCommandController.class)
public final class DeviceCommandExceptionHandler {
    @ExceptionHandler(DeviceCommandAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "DEVICE_COMMAND_ACCESS_DENIED"));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<Map<String, String>> conflict(IdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_CONFLICT",
                "message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_DEVICE_COMMAND",
                "message", exception.getMessage()));
    }
}

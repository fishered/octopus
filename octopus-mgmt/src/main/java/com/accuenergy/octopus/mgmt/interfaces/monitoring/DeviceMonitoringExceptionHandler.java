package com.accuenergy.octopus.mgmt.interfaces.monitoring;

import com.accuenergy.octopus.mgmt.application.monitoring.DeviceMonitoringAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.monitoring.DesiredShadowIdempotencyConflictException;
import com.accuenergy.octopus.mgmt.application.monitoring.DesiredShadowVersionConflictException;
import com.accuenergy.octopus.mgmt.application.monitoring.DeviceMonitoringNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeviceMonitoringController.class)
public final class DeviceMonitoringExceptionHandler {
    @ExceptionHandler(DeviceMonitoringAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(DeviceMonitoringAccessDeniedException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", failure.getMessage()));
    }

    @ExceptionHandler({DesiredShadowVersionConflictException.class,
            DesiredShadowIdempotencyConflictException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", failure.getMessage()));
    }

    @ExceptionHandler(DeviceMonitoringNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(DeviceMonitoringNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", failure.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(Map.of("error", failure.getMessage()));
    }
}

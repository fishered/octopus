package com.accuenergy.octopus.mgmt.interfaces.dashboard;

import com.accuenergy.octopus.mgmt.application.dashboard.DashboardAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.dashboard.DashboardVersionConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public final class DashboardExceptionHandler {
    @ExceptionHandler(DashboardAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "DASHBOARD_ACCESS_DENIED"));
    }

    @ExceptionHandler(DashboardVersionConflictException.class)
    ResponseEntity<Map<String, String>> versionConflict(DashboardVersionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "DASHBOARD_VERSION_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_DASHBOARD_REQUEST",
                "message", exception.getMessage()));
    }
}

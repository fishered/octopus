package com.accuenergy.octopus.mgmt.interfaces.analytics;

import com.accuenergy.octopus.mgmt.application.analytics.AnalyticsAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TelemetryAnalyticsController.class)
public final class AnalyticsExceptionHandler {
    @ExceptionHandler(AnalyticsAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ANALYTICS_ACCESS_DENIED"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ANALYTICS_QUERY",
                "message", exception.getMessage()));
    }
}

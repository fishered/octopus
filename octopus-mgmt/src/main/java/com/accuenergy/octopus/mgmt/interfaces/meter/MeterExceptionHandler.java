package com.accuenergy.octopus.mgmt.interfaces.meter;

import com.accuenergy.octopus.mgmt.application.meter.MeterAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MeterController.class)
public final class MeterExceptionHandler {
    @ExceptionHandler(MeterAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "METER_ACCESS_DENIED"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_METER_REQUEST",
                "message", exception.getMessage()));
    }
}

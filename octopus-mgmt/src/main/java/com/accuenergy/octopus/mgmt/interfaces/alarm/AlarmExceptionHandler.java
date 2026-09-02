package com.accuenergy.octopus.mgmt.interfaces.alarm;

import com.accuenergy.octopus.mgmt.application.alarm.AlarmAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AlarmRuleController.class, AlarmIncidentController.class})
public final class AlarmExceptionHandler {
    @ExceptionHandler(AlarmAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ALARM_ACCESS_DENIED"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ALARM_REQUEST",
                "message", exception.getMessage()));
    }
}

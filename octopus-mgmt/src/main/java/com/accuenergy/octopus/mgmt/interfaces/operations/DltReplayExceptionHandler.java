package com.accuenergy.octopus.mgmt.interfaces.operations;

import com.accuenergy.octopus.mgmt.application.operations.DltReplayAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayConflictException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayInfrastructureException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayLimitExceededException;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DltReplayController.class)
public final class DltReplayExceptionHandler {
    @ExceptionHandler(DltReplayAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(DltReplayAccessDeniedException failure) {
        return response(HttpStatus.FORBIDDEN, "KAFKA_DLT_REPLAY_ACCESS_DENIED", failure.getMessage());
    }

    @ExceptionHandler(DltReplayNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(DltReplayNotFoundException failure) {
        return response(HttpStatus.NOT_FOUND, "KAFKA_DLT_RECORD_NOT_FOUND", failure.getMessage());
    }

    @ExceptionHandler({DltReplayConflictException.class, DltReplayLimitExceededException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException failure) {
        return response(HttpStatus.CONFLICT, "KAFKA_DLT_REPLAY_CONFLICT", failure.getMessage());
    }

    @ExceptionHandler(DltReplayInfrastructureException.class)
    ResponseEntity<Map<String, String>> unavailable(DltReplayInfrastructureException failure) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "KAFKA_DLT_REPLAY_UNAVAILABLE", failure.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException failure) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_KAFKA_DLT_REPLAY", failure.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}

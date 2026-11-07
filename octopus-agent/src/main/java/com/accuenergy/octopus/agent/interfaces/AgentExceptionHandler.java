package com.accuenergy.octopus.agent.interfaces;

import com.accuenergy.octopus.agent.application.AiBudgetExceededException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeviceIntegrationController.class)
public final class AgentExceptionHandler {
    @ExceptionHandler(AiBudgetExceededException.class)
    ResponseEntity<Map<String, String>> budgetExceeded() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body(Map.of("code", "AI_BUDGET_EXCEEDED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_AI_DRAFT_REQUEST"));
    }

    @ExceptionHandler({IllegalStateException.class, CompletionException.class})
    ResponseEntity<Map<String, String>> unavailable(RuntimeException failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof AiBudgetExceededException) return budgetExceeded();
        if (cause instanceof IllegalArgumentException) return invalid();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("code", "AI_DRAFT_UNAVAILABLE"));
    }
}

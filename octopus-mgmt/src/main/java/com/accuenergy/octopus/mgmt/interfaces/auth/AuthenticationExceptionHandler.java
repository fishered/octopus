package com.accuenergy.octopus.mgmt.interfaces.auth;

import com.accuenergy.octopus.mgmt.application.identity.AuthenticationFailedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthenticationController.class, TotpController.class})
public final class AuthenticationExceptionHandler {
    @ExceptionHandler({AuthenticationFailedException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> authenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("code", "AUTHENTICATION_FAILED", "message", "Invalid credentials",
                        "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("code", "INVALID_AUTHENTICATION_REQUEST", "message", exception.getMessage(),
                        "timestamp", Instant.now().toString()));
    }
}

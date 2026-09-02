package com.accuenergy.octopus.mgmt.interfaces.iam;

import com.accuenergy.octopus.mgmt.application.iam.IamAccessDeniedException;
import com.accuenergy.octopus.mgmt.application.identity.AuthenticationFailedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IamAdministrationController.class)
public final class IamAdministrationExceptionHandler {
    @ExceptionHandler(IamAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "IAM_ACCESS_DENIED"));
    }
    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<Map<String, String>> authenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("code", "AUTHENTICATION_FAILED", "message", "Invalid credentials"));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_IAM_REQUEST", "message", exception.getMessage()));
    }
}

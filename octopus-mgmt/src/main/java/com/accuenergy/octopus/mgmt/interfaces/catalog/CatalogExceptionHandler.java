package com.accuenergy.octopus.mgmt.interfaces.catalog;

import com.accuenergy.octopus.mgmt.application.catalog.CatalogAccessDeniedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public final class CatalogExceptionHandler {
    @ExceptionHandler(CatalogAccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "CATALOG_ACCESS_DENIED"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_CATALOG_REQUEST",
                "message", exception.getMessage()));
    }
}

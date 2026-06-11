package com.codelens.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Handle all RuntimeExceptions ──────────────────────────
    // Covers: project not found, already exists, wrong status, etc.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e) {

        log.error("❌ RuntimeException: {}", e.getMessage());

        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // ── Handle Access Denied ───────────────────────────────────
    // Covers: project doesn't belong to this user
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException e) {

        log.warn("🔒 Access denied: {}", e.getMessage());

        return buildResponse(HttpStatus.FORBIDDEN,
                "Access denied: " + e.getMessage());
    }

    // ── Handle Illegal State ───────────────────────────────────
    // Covers: GEMINI_API_KEY not set, wrong config, etc.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException e) {

        log.error("❌ IllegalStateException: {}", e.getMessage());

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage());
    }

    // ── Handle All Other Unexpected Exceptions ─────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception e) {

        log.error("❌ Unexpected error: {}", e.getMessage(), e);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again.");
    }

    // ── Build Consistent Error Response Body ───────────────────
    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message) {

        // LinkedHashMap preserves insertion order in JSON
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);

        return ResponseEntity.status(status).body(body);
    }
}
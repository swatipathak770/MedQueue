package com.medqueue.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException ex) { return error(HttpStatus.CONFLICT, ex.getMessage()); }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException ex) { return error(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, Object>> badCredentials() { return error(HttpStatus.UNAUTHORIZED, "Invalid email or password"); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> forbidden() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken;
        return error(anonymous ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                anonymous ? "Authentication is required" : "Access is not allowed");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "Request validation failed"); body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(base(status, message));
    }
    private Map<String, Object> base(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now()); body.put("status", status.value()); body.put("error", status.getReasonPhrase()); body.put("message", message);
        return body;
    }
}

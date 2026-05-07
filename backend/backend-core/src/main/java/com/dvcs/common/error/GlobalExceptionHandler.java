package com.dvcs.common.error;

import com.dvcs.auth.exception.ConflictException;
import com.dvcs.auth.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that maps domain exceptions to HTTP error responses
 * using the standard {@link ErrorEnvelope} format.
 *
 * <p>All 4xx and 5xx responses use the format:
 * {@code { error, message, details, timestamp }}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @ExceptionHandler(com.dvcs.common.exception.EntityNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleEntityNotFound(
            com.dvcs.common.exception.EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleJpaEntityNotFound(
            jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    @ExceptionHandler(com.dvcs.common.exception.AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(
            com.dvcs.common.exception.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of("ACCESS_DENIED", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of("ACCESS_DENIED", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorEnvelope.of("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(com.dvcs.common.exception.ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleCommonConflict(
            com.dvcs.common.exception.ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorEnvelope.of("CONFLICT", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 401 Unauthorized
    // -------------------------------------------------------------------------

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorEnvelope> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorEnvelope.of("UNAUTHORIZED", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request — validation errors
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(com.dvcs.common.exception.PathTraversalException.class)
    public ResponseEntity<ErrorEnvelope> handlePathTraversal(
            com.dvcs.common.exception.PathTraversalException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("PATH_TRAVERSAL", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity — business rule violations
    // -------------------------------------------------------------------------

    @ExceptionHandler(com.dvcs.common.exception.InvalidRequestException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidRequest(
            com.dvcs.common.exception.InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorEnvelope.of("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(com.dvcs.common.exception.MergeConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleMergeConflict(
            com.dvcs.common.exception.MergeConflictException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorEnvelope.of("MERGE_CONFLICT", ex.getMessage()));
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error — catch-all
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

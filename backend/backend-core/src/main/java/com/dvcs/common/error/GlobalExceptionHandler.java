package com.dvcs.common.error;

import com.dvcs.auth.exception.ConflictException;
import com.dvcs.auth.exception.UnauthorizedException;
import com.dvcs.common.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 *
 * <p>Mapping table:
 * <ul>
 *   <li>{@link com.dvcs.common.exception.EntityNotFoundException} → 404</li>
 *   <li>{@link com.dvcs.common.exception.AccessDeniedException} → 403</li>
 *   <li>{@link ConflictException} / {@link com.dvcs.common.exception.ConflictException} → 409</li>
 *   <li>{@link com.dvcs.common.exception.PathTraversalException} → 400</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 with field-level details</li>
 *   <li>{@link RateLimitExceededException} → 429 with {@code Retry-After} header</li>
 *   <li>{@link com.dvcs.common.exception.MergeConflictException} → 422</li>
 *   <li>{@link com.dvcs.common.exception.InvalidRequestException} → 422</li>
 *   <li>{@link UnauthorizedException} → 401</li>
 *   <li>{@link Exception} (catch-all) → 500</li>
 * </ul>
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of("BAD_REQUEST", "Malformed JSON request body"));
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
    // 429 Too Many Requests — rate limit exceeded
    // -------------------------------------------------------------------------

    /**
     * Handles {@link RateLimitExceededException} thrown by services or filters.
     *
     * <p>Sets the {@code Retry-After} header so clients know when to retry.
     * Note: the {@link com.dvcs.common.security.RateLimitFilter} writes 429 directly
     * to the response before the controller layer is reached; this handler covers
     * any programmatic throws from service code.
     *
     * @param ex the rate-limit exception
     * @return HTTP 429 with error envelope and {@code Retry-After} header
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleRateLimitExceeded(RateLimitExceededException ex) {
        Map<String, Object> details = Map.of("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorEnvelope.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), details));
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

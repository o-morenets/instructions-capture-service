package com.example.instructions.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

/**
 * Global exception handler for REST controllers.
 * Provides consistent, structured JSON error responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle validation errors (e.g. from TradeTransformer.validateCanonicalTrade)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(IllegalArgumentException e) {
        log.warn("Validation error: {}", e.getMessage());
        return buildResponse("Validation failed: " + e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    /**
     * Handle file size exceeded errors
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.warn("File size exceeded: {}", e.getMessage());
        return buildResponse(
                "File size exceeds the maximum allowed limit of 10 MiB (10,485,760 bytes)",
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_SIZE_EXCEEDED"
        );
    }

    /**
     * Handle runtime exceptions (unexpected business errors)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        return buildResponse(
                "An error occurred while processing your request: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RUNTIME_ERROR"
        );
    }

    /**
     * Handle generic exceptions (ultimate fallback)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return buildResponse(
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR"
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(String message, HttpStatus status, String errorCode) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(false, message, errorCode, status.value(), Instant.now()));
    }

    /**
     * DTO for structured error response
     */
    public record ErrorResponse(
            boolean success,
            String error,
            String errorCode,
            int status,
            Instant timestamp
    ) {
    }
}

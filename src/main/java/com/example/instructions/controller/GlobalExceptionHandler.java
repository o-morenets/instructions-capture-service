package com.example.instructions.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application
 * Provides consistent error responses across all controllers
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors (from TradeTransformer.validateCanonicalTrade)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(IllegalArgumentException e) {
        log.warn("Validation error: {}", e.getMessage());

        Map<String, Object> errorResponse = createErrorResponse(
                "Validation failed: " + e.getMessage(),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle file size exceeded errors
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException e) {
        log.warn("File size exceeded: {}", e.getMessage());

        Map<String, Object> errorResponse = createErrorResponse(
                "File size exceeds the maximum allowed limit of 10 MiB (10,485,760 bytes)",
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_SIZE_EXCEEDED"
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handle runtime exceptions (fallback for unexpected errors)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = createErrorResponse(
                "An error occurred while processing your request: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RUNTIME_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle generic exceptions (ultimate fallback)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = createErrorResponse(
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Create standardized error response
     */
    private Map<String, Object> createErrorResponse(String message, HttpStatus status, String errorCode) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        errorResponse.put("errorCode", errorCode);
        errorResponse.put("status", status.value());
        errorResponse.put("timestamp", LocalDateTime.now());

        return errorResponse;
    }
}

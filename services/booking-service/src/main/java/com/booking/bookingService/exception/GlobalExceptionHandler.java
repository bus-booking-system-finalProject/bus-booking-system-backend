package com.booking.bookingService.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle Resource Not Found (404)
    // Example: Trip not found, Bus not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // 2. Handle Conflict / Illegal State (409 or 400)
    // Example: Seat already booked, Booking already cancelled
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        // returning CONFLICT (409) is often more semantic for state errors than BAD_REQUEST
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 3. Handle Validation Errors (400)
    // Example: Invalid email format, missing required fields
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Collect errors into a simple Map<Field, Message>
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.toMap(
                        error -> ((FieldError) error).getField(),
                        error -> error.getDefaultMessage(),
                        (existing, replacement) -> existing // Keep first error if duplicate
                ));
        
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    // 4. Handle General Exceptions (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        // In a real app, use a Logger here: log.error("Internal Error", ex);
        ex.printStackTrace(); 
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    // Helper to keep response format consistent
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("data", data);
        response.put("message", message);
        return new ResponseEntity<>(response, status);
    }
}
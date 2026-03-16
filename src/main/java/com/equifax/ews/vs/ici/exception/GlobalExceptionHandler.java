package com.equifax.ews.vs.ici.exception;

import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.equifax.ews.vs.ici.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * Global exception handler for REST API errors.
 * Converts exceptions to standardized ErrorResponse format with logging.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors (e.g., @Valid fields).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String correlationId = MDCUtil.getCorrelationId();
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error - correlationId: {}, errors: {}, path: {}",
                correlationId, errors, request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(errors)
                .correlationId(correlationId)
                .timestamp(System.currentTimeMillis())
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        String correlationId = MDCUtil.getCorrelationId();
        log.warn("Illegal argument error - correlationId: {}, message: {}, path: {}",
                correlationId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_REQUEST")
                .message(ex.getMessage())
                .correlationId(correlationId)
                .timestamp(System.currentTimeMillis())
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle runtime exceptions (internal errors).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        String correlationId = MDCUtil.getCorrelationId();
        log.error("Runtime error - correlationId: {}, message: {}, path: {}",
                correlationId, ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("An error occurred processing your request")
                .details(ex.getMessage())
                .correlationId(correlationId)
                .timestamp(System.currentTimeMillis())
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            HttpServletRequest request) {

        String correlationId = MDCUtil.getCorrelationId();
        log.error("Unexpected error - correlationId: {}, exception: {}, message: {}, path: {}",
                correlationId, ex.getClass().getName(), ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .details(ex.getMessage())
                .correlationId(correlationId)
                .timestamp(System.currentTimeMillis())
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
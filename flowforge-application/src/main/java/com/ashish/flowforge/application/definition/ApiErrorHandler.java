package com.ashish.flowforge.application.definition;

import com.ashish.flowforge.engine.definition.DefinitionValidationException;
import com.ashish.flowforge.engine.definition.ValidationError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ApiError(
            String timestamp,
            int status,
            String code,
            String message,
            Object details,
            String correlationId) {}

    @ExceptionHandler(DefinitionValidationException.class)
    ResponseEntity<ApiError> validation(
            DefinitionValidationException ex,
            HttpServletRequest request) {
        ValidationError first = ex.getErrors().getFirst();
        return ResponseEntity.badRequest().body(new ApiError(
                OffsetDateTime.now().toString(),
                400,
                first.code(),
                first.message(),
                ex.getErrors(),
                correlationId(request)
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedRequest(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                OffsetDateTime.now().toString(),
                400,
                "INVALID_WORKFLOW_DEFINITION",
                "Request body is malformed.",
                List.of(),
                correlationId(request)
        ));
    }

    @ExceptionHandler(DefinitionException.class)
    ResponseEntity<ApiError> definition(
            DefinitionException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(new ApiError(
                OffsetDateTime.now().toString(),
                ex.status(),
                ex.code(),
                ex.getMessage(),
                List.of(),
                correlationId(request)
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(
            Exception ex,
            HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(new ApiError(
                OffsetDateTime.now().toString(),
                500,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                List.of(),
                correlationId(request)
        ));
    }

    private String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Correlation-ID");
        return supplied == null || supplied.isBlank()
                ? UUID.randomUUID().toString()
                : supplied;
    }
}

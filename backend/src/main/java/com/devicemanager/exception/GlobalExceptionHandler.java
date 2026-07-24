package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        return build(ex.getStatusCode().value(), ex.getReason(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST.value(), message, request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String raw = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String lower = raw == null ? "" : raw.toLowerCase();
        String message;
        if (lower.contains("uk_mas_numero") || lower.contains("numero")) {
            message = "Numéro MAS déjà utilisé";
        } else if (lower.contains("uk_sfm_nom") || (lower.contains("sfm") && lower.contains("nom"))) {
            message = "Nom SFM déjà utilisé";
        } else if (lower.contains("uk_device_nom") || (lower.contains("device") && lower.contains("nom"))) {
            message = "Nom de pièce déjà utilisé";
        } else if (lower.contains("uk_device_reference") || lower.contains("reference")) {
            message = "Référence déjà utilisée";
        } else if (lower.contains("marque") || lower.contains("label") || lower.contains("code")) {
            message = "Nom de marque déjà utilisé";
        } else if (lower.contains("foreign key") || lower.contains("constraint")) {
            message = "Suppression impossible : cet enregistrement est encore référencé";
        } else {
            message = "Contrainte d'unicité violée";
        }
        return build(HttpStatus.CONFLICT.value(), message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<ApiError> build(int status, String message, String path) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(HttpStatus.valueOf(status).getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}

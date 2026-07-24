package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerTest.class);

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock private HttpServletRequest request;

    @Test
    void handleResponseStatusException() {
        log.info("Test exception handler");
        when(request.getRequestURI()).thenReturn("/api/mas");

        ResponseEntity<ApiError> response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Numéro MAS déjà utilisé dans cet atelier"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Numéro MAS");
    }

    @Test
    void handleIntegrity_mapsUniqueNumero() {
        when(request.getRequestURI()).thenReturn("/api/mas");

        ResponseEntity<ApiError> response = handler.handleIntegrity(
                new DataIntegrityViolationException("Duplicate", new RuntimeException("uk_mas_numero_atelier")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Numéro MAS déjà utilisé");
    }

    @Test
    void handleIntegrity_mapsForeignKey() {
        when(request.getRequestURI()).thenReturn("/api/devices");

        ResponseEntity<ApiError> response = handler.handleIntegrity(
                new DataIntegrityViolationException("FK", new RuntimeException("foreign key constraint fails")),
                request);

        assertThat(response.getBody().getMessage()).contains("référencé");
    }
}

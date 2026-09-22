package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

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

        assertThat(response.getBody().getMessage()).contains("encore utilisé");
    }

    @Test
    void handleIntegrity_mysqlFkWithReferences_doesNotLookLikeDeviceReference() {
        when(request.getRequestURI()).thenReturn("/api/devices/40");

        String mysql = "Cannot delete or update a parent row: a foreign key constraint fails "
                + "(`dm`.`commande_ligne`, CONSTRAINT `fk_commande_ligne_device` "
                + "FOREIGN KEY (`device_id`) REFERENCES `device` (`id`))";
        ResponseEntity<ApiError> response = handler.handleIntegrity(
                new DataIntegrityViolationException("FK", new RuntimeException(mysql)),
                request);

        assertThat(response.getBody().getMessage()).contains("encore utilisé");
        assertThat(response.getBody().getMessage()).doesNotContain("Référence déjà utilisée");
    }

    @Test
    void handleDatabaseUnavailable_returns503() {
        when(request.getRequestURI()).thenReturn("/api/devices");

        ResponseEntity<ApiError> response = handler.handleDatabaseUnavailable(
                new CannotGetJdbcConnectionException("Pool", new SQLException("Communications link failure")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("temporairement indisponible");
    }

    @Test
    void handleIllegalArgument_returns400WithSafeMessage() {
        when(request.getRequestURI()).thenReturn("/api/mas");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("objectKey invalide"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("objectKey invalide");
    }

    @Test
    void handleIllegalArgument_hidesTechnicalDetails() {
        when(request.getRequestURI()).thenReturn("/api/mas");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("java.sql.SQLException: something"),
                request);

        assertThat(response.getBody().getMessage()).doesNotContain("SQLException");
        assertThat(response.getBody().getMessage()).contains("Requête invalide");
    }

    @Test
    void handleNotReadable_returns400() {
        when(request.getRequestURI()).thenReturn("/api/order-requests/mail-preview");

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "no body", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<ApiError> response = handler.handleNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("absent ou mal formé");
    }

    @Test
    void handleMissingPart_mentionsPartName() {
        when(request.getRequestURI()).thenReturn("/api/ai/scan-label");

        ResponseEntity<ApiError> response = handler.handleMissingPart(
                new MissingServletRequestPartException("image"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("image");
    }

    @Test
    void handleEntityNotFound_returns404() {
        when(request.getRequestURI()).thenReturn("/api/mas/999");

        ResponseEntity<ApiError> response = handler.handleEntityNotFound(
                new EntityNotFoundException("MAS 999 not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Élément introuvable.");
    }

    @Test
    void handleStorageUnavailable_returns503() {
        when(request.getRequestURI()).thenReturn("/api/mas/1/regle-jeux/pdf");

        ResponseEntity<ApiError> response = handler.handleStorageUnavailable(
                new StorageUnavailableException("Stockage cloud injoignable."), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).contains("Stockage cloud injoignable");
    }

    @Test
    void handleStorageUnavailable_fallbackMessageIfBlank() {
        when(request.getRequestURI()).thenReturn("/api/x");

        ResponseEntity<ApiError> response = handler.handleStorageUnavailable(
                new StorageUnavailableException(""), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).contains("Stockage cloud");
    }
}

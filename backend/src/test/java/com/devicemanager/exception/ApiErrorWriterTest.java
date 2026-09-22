package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ApiErrorWriter writer = new ApiErrorWriter(objectMapper);

    @Test
    void writesJsonBodyWithStatusAndPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.FORBIDDEN, "Vous n'avez pas les droits pour cette action.");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getError()).isEqualTo("Forbidden");
        assertThat(body.getMessage()).isEqualTo("Vous n'avez pas les droits pour cette action.");
        assertThat(body.getPath()).isEqualTo("/api/devices");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void writesUnicodeMessageWithoutMojibake() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.BAD_REQUEST,
                "Numéro d'atelier invalide (résélectionnez).");

        ApiError body = objectMapper.readValue(response.getContentAsByteArray(), ApiError.class);
        assertThat(body.getMessage()).isEqualTo("Numéro d'atelier invalide (résélectionnez).");
    }

    @Test
    void doesNothingWhenResponseAlreadyCommitted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // MockHttpServletResponse ne se committe pas automatiquement : on force le flag via setCommitted.
        response.setCommitted(true);

        writer.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        assertThat(response.getContentAsString()).isEmpty();
        // Le statut par défaut de MockHttpServletResponse est 200 : rien n'a été écrit.
        assertThat(response.getStatus()).isEqualTo(200);
    }
}

package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ApiAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new ApiAuthenticationEntryPoint(new ApiErrorWriter(objectMapper));
    }

    @Test
    void writes401WithGenericMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getMessage()).contains("Authentification requise");
        assertThat(body.getPath()).isEqualTo("/api/devices");
    }

    @Test
    void writesSessionExpiredWhenCauseIsExpiredJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = Mockito.mock(Claims.class);
        ExpiredJwtException expired = new ExpiredJwtException(null, claims, "JWT expired at ...");

        entryPoint.commence(request, response, new BadCredentialsException("token expired", expired));

        assertThat(response.getStatus()).isEqualTo(401);
        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getMessage()).contains("session a expiré");
    }

    @Test
    void writesSessionExpiredForCredentialsExpiredException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new CredentialsExpiredException("expired"));

        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getMessage()).contains("session a expiré");
    }
}

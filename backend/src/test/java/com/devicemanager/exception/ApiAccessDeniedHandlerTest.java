package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(new ApiErrorWriter(objectMapper));

    @Test
    void writes403WithGenericMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/setup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getMessage()).contains("droits");
        assertThat(body.getPath()).isEqualTo("/api/setup");
    }
}

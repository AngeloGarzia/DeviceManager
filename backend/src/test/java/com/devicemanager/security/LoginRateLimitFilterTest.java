package com.devicemanager.security;

import com.devicemanager.dto.ApiError;
import com.devicemanager.exception.ApiErrorWriter;
import com.devicemanager.security.ratelimit.RateLimitStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitFilterTest {

    @Mock private RateLimitStore rateLimitStore;
    @Mock private FilterChain chain;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter(rateLimitStore, new ApiErrorWriter(objectMapper), 20);
    }

    @Test
    void skipsNonLoginRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimitStore, never()).tryAcquire(any(), anyInt(), any());
    }

    @Test
    void skipsGetOnLoginRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimitStore, never()).tryAcquire(any(), anyInt(), any());
    }

    @Test
    void allowsRequestWhenUnderLimit() throws Exception {
        when(rateLimitStore.tryAcquire(any(), eq(20), any(Duration.class))).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksWith429WhenLimitReached() throws Exception {
        when(rateLimitStore.tryAcquire(any(), eq(20), any(Duration.class))).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
        request.setRemoteAddr("10.0.0.42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);

        ApiError body = objectMapper.readValue(response.getContentAsString(), ApiError.class);
        assertThat(body.getStatus()).isEqualTo(429);
        assertThat(body.getMessage()).contains("Trop de tentatives");
        assertThat(body.getPath()).isEqualTo("/api/auth/forgot-password");
    }

    @Test
    void extractsFirstIpFromXForwardedFor() throws Exception {
        when(rateLimitStore.tryAcquire(eq("203.0.113.5"), anyInt(), any(Duration.class))).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/reset-password");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        request.setRemoteAddr("10.0.0.99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(rateLimitStore).tryAcquire(eq("203.0.113.5"), anyInt(), any(Duration.class));
        verify(chain).doFilter(request, response);
    }
}

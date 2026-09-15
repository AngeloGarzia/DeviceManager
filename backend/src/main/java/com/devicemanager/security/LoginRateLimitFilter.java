package com.devicemanager.security;

import com.devicemanager.security.ratelimit.RateLimitStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Limite le débit des tentatives d'auth publiques par adresse IP
 * ({@code login}, {@code forgot-password}, {@code reset-password}), via {@link RateLimitStore}.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password");
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limitPerMinute;
    private final RateLimitStore rateLimitStore;

    public LoginRateLimitFilter(
            RateLimitStore rateLimitStore,
            @Value("${app.security.login-rate-limit-per-minute:20}") int limitPerMinute) {
        this.rateLimitStore = rateLimitStore;
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !LIMITED_PATHS.contains(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String ip = clientIp(request);
        if (!rateLimitStore.tryAcquire(ip, limitPerMinute, WINDOW)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"message\":\"Trop de tentatives. Réessayez plus tard.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}

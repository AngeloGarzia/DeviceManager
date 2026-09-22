package com.devicemanager.exception;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Entry point Spring Security : renvoie 401 au format {@link com.devicemanager.dto.ApiError}
 * lorsque l'accès est refusé faute d'authentification (token absent, expiré, malformé).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MSG_EXPIRED =
            "Votre session a expiré. Veuillez vous reconnecter.";
    private static final String MSG_REQUIRED =
            "Authentification requise. Veuillez vous reconnecter.";

    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = MSG_REQUIRED;
        Throwable cause = authException;
        while (cause != null) {
            if (cause instanceof ExpiredJwtException || cause instanceof CredentialsExpiredException) {
                message = MSG_EXPIRED;
                break;
            }
            cause = cause.getCause();
        }
        log.debug("Auth refusée sur {}: {}", request.getRequestURI(), authException.getMessage());
        apiErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED, message);
    }
}

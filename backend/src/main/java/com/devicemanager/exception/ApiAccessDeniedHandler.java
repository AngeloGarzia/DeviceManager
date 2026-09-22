package com.devicemanager.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handler Spring Security : renvoie 403 au format {@link com.devicemanager.dto.ApiError}
 * lorsque l'utilisateur authentifié n'a pas les droits requis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MSG_FORBIDDEN =
            "Vous n'avez pas les droits pour cette action.";

    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.debug("Accès refusé sur {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
        apiErrorWriter.write(request, response, HttpStatus.FORBIDDEN, MSG_FORBIDDEN);
    }
}

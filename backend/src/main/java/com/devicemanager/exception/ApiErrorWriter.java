package com.devicemanager.exception;

import com.devicemanager.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Écrit une réponse d'erreur uniforme {@link ApiError} depuis un filtre ou un handler Spring Security,
 * afin de conserver le même format que {@link GlobalExceptionHandler} pour toute la surface API.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * Sérialise {@link ApiError} dans la réponse HTTP avec le statut fourni.
     *
     * @param request  requête en cours (pour le chemin)
     * @param response réponse à écrire
     * @param status   statut HTTP à renvoyer
     * @param message  message métier destiné à l'utilisateur (jamais de détail technique)
     * @throws IOException en cas d'échec d'écriture (rare, propagé par le filtre)
     */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}

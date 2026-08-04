package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Corps de réponse standard pour les erreurs HTTP de l'API.
 */
@Data
@Builder
public class ApiError {

    /** Horodatage de l'erreur. */
    private Instant timestamp;
    /** Code HTTP numérique. */
    private int status;
    /** Libellé HTTP (ex. {@code Bad Request}). */
    private String error;
    /** Message d'erreur lisible pour l'utilisateur ou le client. */
    private String message;
    /** Chemin de la requête ayant provoqué l'erreur. */
    private String path;
}

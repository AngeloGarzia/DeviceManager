package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Requête de sélection ou mémorisation de l'atelier de travail préféré.
 */
@Data
public class PreferredAtelierRequest {

    /** Identifiant de l'atelier à activer ou mémoriser. */
    @NotNull(message = "Sélectionnez un atelier")
    private Long atelierId;
}

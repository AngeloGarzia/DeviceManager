package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Requête de mise à jour groupée des paramètres applicatifs.
 */
@Data
public class AppSettingsUpdateRequest {

    /** Map clé → nouvelle valeur pour chaque paramètre à modifier. */
    @NotNull(message = "Les valeurs des paramètres sont obligatoires")
    private Map<String, String> values;
}

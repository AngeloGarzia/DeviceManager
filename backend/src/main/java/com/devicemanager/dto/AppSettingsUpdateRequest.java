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
    @NotNull
    private Map<String, String> values;
}

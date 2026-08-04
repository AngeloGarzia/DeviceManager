package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Représentation d'un paramètre applicatif exposé par l'API d'administration.
 */
@Data
@Builder
public class AppSettingResponse {
    /** Clé du paramètre. */
    private String key;
    /** Valeur actuelle (masquée si secret). */
    private String value;
    private String label;
    private String category;
    /** Indique si la valeur est masquée côté client. */
    private boolean secret;
}

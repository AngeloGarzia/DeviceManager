package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Représentation d'une marque du catalogue MAS.
 */
@Data
@Builder
public class MarqueMasResponse {
    private Long id;
    private String code;
    private String label;
    /** Alias de {@link #id} pour compatibilité front (anciennement valeur enum). */
    private Long value;
}

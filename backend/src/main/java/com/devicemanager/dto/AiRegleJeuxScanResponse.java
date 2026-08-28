package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat de l'analyse IA d'un PDF de règle de jeux.
 */
@Data
@Builder
public class AiRegleJeuxScanResponse {
    private boolean enabled;
    /** Libellé proposé pour le catalogue (max 200 car.). */
    private String label;
    /** Description synthétique (max 500 car.). */
    private String description;
    /** Incertitudes ou message si l'IA est indisponible. */
    private String notes;
}

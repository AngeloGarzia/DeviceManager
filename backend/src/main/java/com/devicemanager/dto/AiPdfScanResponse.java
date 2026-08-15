package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat de l'analyse IA d'un PDF (manuel / datasheet / notice).
 */
@Data
@Builder
public class AiPdfScanResponse {
    private boolean enabled;
    /** Texte technique synthétisé pour le champ {@code informationTechnique}. */
    private String informationTechnique;
    private String notes;
}

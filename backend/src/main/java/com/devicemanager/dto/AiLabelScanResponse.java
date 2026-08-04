package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat de l'analyse IA d'une étiquette de pièce (scan photo).
 */
@Data
@Builder
public class AiLabelScanResponse {
    /** Indique si le scan IA est activé et disponible. */
    private boolean enabled;
    private String nom;
    private String reference;
    private String marque;
    private String usage;
    /** Texte brut extrait de l'étiquette. */
    private String rawText;
    /** Remarques ou incertitudes signalées par l'IA. */
    private String notes;
}

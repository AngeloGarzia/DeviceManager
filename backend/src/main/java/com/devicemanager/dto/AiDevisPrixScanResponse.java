package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Résultat de l'extraction IA des prix d'un devis.
 */
@Data
@Builder
public class AiDevisPrixScanResponse {
    private boolean enabled;
    private String notes;
    private List<AiDevisPrixSuggestion> suggestions;
    private List<AiDevisUnmatchedPart> unmatched;
}

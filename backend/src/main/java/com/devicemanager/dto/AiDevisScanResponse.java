package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Résultat de l'analyse IA d'un devis PDF lié à une commande.
 */
@Data
@Builder
public class AiDevisScanResponse {
    private boolean enabled;
    private String notes;
    private List<AiDevisSuggestion> suggestions;
    private List<AiDevisUnmatchedPart> unmatched;
}

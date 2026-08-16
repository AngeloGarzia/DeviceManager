package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Proposition de mise à jour d'une pièce à partir d'un devis PDF.
 */
@Data
@Builder
public class AiDevisSuggestion {
    private Long deviceId;
    private String currentNom;
    private String currentReference;
    private String suggestedNom;
    private String suggestedReference;
    /** {@code HIGH}, {@code MEDIUM} ou {@code LOW}. */
    private String confidence;
    private boolean hasChanges;
}

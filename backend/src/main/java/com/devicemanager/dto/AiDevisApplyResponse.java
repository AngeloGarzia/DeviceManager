package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Résultat de l'application des mises à jour devis → pièces.
 */
@Data
@Builder
public class AiDevisApplyResponse {
    private OrderRequestResponse order;
    private int updatedCount;
    private List<String> errors;
}

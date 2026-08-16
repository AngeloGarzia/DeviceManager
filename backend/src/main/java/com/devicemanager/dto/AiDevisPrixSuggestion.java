package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Suggestion IA d'un prix unitaire HT extrait d'un devis pour une pièce.
 */
@Data
@Builder
public class AiDevisPrixSuggestion {
    private Long deviceId;
    private String currentNom;
    private String currentReference;
    private BigDecimal lastUnitPriceHt;
    private BigDecimal suggestedUnitPriceHt;
    private Integer quantityOnQuote;
    private String devisDesignation;
    private String devisReference;
    private String confidence;
}

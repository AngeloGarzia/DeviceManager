package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Résultat de l'analyse IA d'une facture / bon de livraison (scan photo).
 */
@Data
@Builder
public class AiFactureScanResponse {
    private boolean enabled;
    private String nom;
    private String reference;
    private String numeroSerie;
    private String marque;
    private String sfmNom;
    private String fournisseur;
    private BigDecimal unitPriceHt;
    /** Date facture au format {@code yyyy-MM-dd} si identifiable. */
    private String dateAcquisition;
    private String usage;
    private String rawText;
    private String notes;
}

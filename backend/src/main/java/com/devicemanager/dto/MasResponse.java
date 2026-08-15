package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Représentation d'une référence MAS renvoyée par l'API.
 */
@Data
@Builder
public class MasResponse {
    private Long id;
    private String numero;
    private String numeroSocle;
    private BigDecimal tauxRedistribution;
    private Long marqueId;
    /** Code court de la marque. */
    private String marque;
    /** Libellé complet de la marque. */
    private String marqueLabel;
    private Long denoId;
    private BigDecimal denoValeur;
    private String denoLabel;
    /** Code statut : UTILISEE | EN_RESERVE | VENDUE | DETRUITE. */
    private String statut;
    /** Libellé statut pour l'UI. */
    private String statutLabel;
    /** true si statut = UTILISEE (compatibilité). */
    private boolean utilise;
}

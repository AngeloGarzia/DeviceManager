package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private LocalDate dateMiseEnService;
    private String typeMachine;
    private String numeroSerie;
    private LocalDate dateCessation;
    private String destinationMachineUsagee;
    /** Bon de destruction (PDF / image) si statut DETRUITE. */
    private String destructionFileUrl;
    private String destructionOriginalName;
    private String destructionContentType;
    private Long destructionFileSize;
    private LocalDateTime destructionUploadedAt;
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

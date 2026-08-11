package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bon d'intervention archivé renvoyé par l'API.
 */
@Data
@Builder
public class InterventionResponse {

    private Long id;
    private String numero;
    private LocalDateTime dateIntervention;
    private String technicienNom;
    private String emplacement;
    private String machineMas;
    private String motif;
    private String diagnostic;
    private String travaux;
    private String observations;
    private LocalDateTime createdAt;
    private int totalPieces;
    private int totalQuantite;
    private List<InterventionLineResponse> lignes;
}

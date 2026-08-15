package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fiche FIT (en-tête + lignes) exposée par l'API.
 */
@Data
@Builder
public class FitResponse {

    private Long id;
    private Long masId;
    private String masNumero;
    private String casinoNom;
    private String numeroMachineCasino;
    private LocalDate dateMiseEnService;
    private String marque;
    private String typeMachine;
    private String numeroSerieMachine;
    private String numeroSerieLecteur;
    private LocalDate dateCessation;
    private String destinationMachineUsagee;
    private String modeleNumero;
    private String referenceLegale;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int totalLignes;
    private List<FitLigneResponse> lignes;
}

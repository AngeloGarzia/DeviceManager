package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ligne d'historique FIT exposée par l'API.
 */
@Data
@Builder
public class FitLigneResponse {

    private Long id;
    private Long interventionId;
    private String interventionNumero;
    private LocalDate dateOperation;
    private String numeroSocle;
    private String numeroEmplacement;
    private String numeroSerieLecteur;
    private BigDecimal tauxRedistribution;
    private BigDecimal valeurUnitaireMises;
    private Long denoId;
    private String denoLabel;
    private String motifNatureOperations;
    private String signatureAdmin;
    private String signatureTechnicien;
    private String signataireAdminNom;
    private String signataireTechnicienNom;
    private boolean signatureDirecteur;
    private LocalDateTime createdAt;
}

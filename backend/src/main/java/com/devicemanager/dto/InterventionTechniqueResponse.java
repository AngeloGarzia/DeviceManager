package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Intervention technique libre exposée par l'API.
 */
@Data
@Builder
public class InterventionTechniqueResponse {

    private Long id;
    private String visiteGroupeId;
    private LocalDateTime dateIntervention;
    private String technicienNom;
    private String emplacement;
    private Long masId;
    private String masNumero;
    private String masMarque;
    private String motif;
    private String diagnostic;
    private String travaux;
    private String observations;
    private Long fitId;
    private Long fitLigneId;
    private Long commandeId;
    private Long bonInterventionId;
    private String bonInterventionNumero;
    private LocalDateTime createdAt;
}

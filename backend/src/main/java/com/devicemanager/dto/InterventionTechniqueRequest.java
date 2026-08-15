package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Création d'intervention(s) technique(s) : une ligne en base par MAS.
 */
@Data
public class InterventionTechniqueRequest {

    @NotNull(message = "La date d'intervention est obligatoire")
    private LocalDateTime dateIntervention;

    @NotEmpty(message = "Sélectionnez au moins une MAS")
    private List<Long> masIds;

    @Size(max = 200)
    private String emplacement;

    @NotBlank(message = "Le motif est obligatoire")
    @Size(max = 500)
    private String motif;

    @Size(max = 2000)
    private String diagnostic;

    @NotBlank(message = "Les travaux sont obligatoires")
    @Size(max = 2000)
    private String travaux;

    @Size(max = 2000)
    private String observations;

    /** Si true : crée aussi une ligne FIT signée pour chaque MAS. */
    private Boolean associerFit;

    private String signatureAdmin;
    private String signatureTechnicien;

    @Size(max = 120)
    private String signataireAdminNom;

    @Size(max = 120)
    private String signataireTechnicienNom;

    /** Demande de commande de pièces (optionnel). */
    private Long commandeId;

    /** Bon d'intervention pièces (optionnel). */
    private Long bonInterventionId;
}

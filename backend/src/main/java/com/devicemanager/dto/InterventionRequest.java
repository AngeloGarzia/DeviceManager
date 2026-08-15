package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Création d'un bon d'intervention avec consommation de pièces.
 */
@Data
public class InterventionRequest {

    @NotNull(message = "La date d'intervention est obligatoire")
    private LocalDateTime dateIntervention;

    @Size(max = 200, message = "L'emplacement ne doit pas dépasser 200 caractères")
    private String emplacement;

    @Size(max = 120, message = "La machine / MAS ne doit pas dépasser 120 caractères")
    private String machineMas;

    /** Identifiant MAS (préféré pour le suivi technique). */
    private Long masId;

    /**
     * Si {@code true} et {@link #masId} renseigné : crée une ligne FIT signée liée à ce bon.
     * Sinon le bon reste indépendant de toute FIT.
     */
    private Boolean associerFit;

    @NotBlank(message = "Le motif de l'intervention est obligatoire")
    @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
    private String motif;

    @Size(max = 2000, message = "Le diagnostic ne doit pas dépasser 2000 caractères")
    private String diagnostic;

    @NotBlank(message = "Les travaux effectués sont obligatoires")
    @Size(max = 2000, message = "Les travaux ne doivent pas dépasser 2000 caractères")
    private String travaux;

    @Size(max = 2000, message = "Les observations ne doivent pas dépasser 2000 caractères")
    private String observations;

    /**
     * Signature manuscrite admin (data URL) — obligatoire si {@link #associerFit} est vrai.
     */
    private String signatureAdmin;

    /**
     * Signature manuscrite technicien (data URL) — obligatoire si {@link #associerFit} est vrai.
     */
    private String signatureTechnicien;

    @Size(max = 120)
    private String signataireAdminNom;

    @Size(max = 120)
    private String signataireTechnicienNom;

    @NotEmpty(message = "Ajoutez au moins une pièce détachée consommée")
    @Valid
    private List<InterventionLineDto> lignes;

    @Data
    public static class InterventionLineDto {
        @NotNull(message = "La pièce est obligatoire sur chaque ligne")
        private Long deviceId;

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être au moins 1")
        private Integer quantite;
    }
}

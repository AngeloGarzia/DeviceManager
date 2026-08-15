package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ajout d'une ligne d'intervention technique sur une FIT (signatures obligatoires).
 */
@Data
public class FitLigneRequest {

    @NotNull(message = "La date d'opération est obligatoire")
    private LocalDate dateOperation;

    @Size(max = 80)
    private String numeroSocle;

    @Size(max = 80)
    private String numeroEmplacement;

    @Size(max = 120)
    private String numeroSerieLecteur;

    private BigDecimal tauxRedistribution;

    private BigDecimal valeurUnitaireMises;

    private Long denoId;

    @NotBlank(message = "Le motif / nature des opérations est obligatoire")
    @Size(max = 2000)
    private String motifNatureOperations;

    @NotBlank(message = "La signature admin est obligatoire")
    private String signatureAdmin;

    @NotBlank(message = "La signature technicien est obligatoire")
    private String signatureTechnicien;

    @Size(max = 120)
    private String signataireAdminNom;

    @Size(max = 120)
    private String signataireTechnicienNom;

    /**
     * Bon d'intervention optionnel à rattacher à cette ligne FIT.
     * Null = ligne FIT indépendante (sans bon).
     */
    private Long interventionId;
}

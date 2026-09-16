package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Bloc obligatoire lors d'un changement de statut MAS : signatures FIT + champs métier.
 */
@Data
public class MasStatutChangeRequest {

    /** Date de la ligne FIT (défaut = aujourd'hui côté service). */
    private LocalDate dateOperation;

    /** Date de cessation MAS / FIT — obligatoire si statut cible VENDUE. */
    private LocalDate dateCessation;

    /** Destination libre (VENDUE) ou forcée côté service (DETRUITE). */
    @Size(max = 255)
    private String destinationMachineUsagee;

    /** CASINO ou SFM si statut cible VENDUE. */
    @Size(max = 20)
    private String acheteurType;

    private Long casinoAcheteurId;

    private Long sfmAcheteurId;

    @Size(max = 2000)
    private String motifNatureOperations;

    @NotBlank
    private String signatureAdmin;

    @NotBlank
    private String signatureTechnicien;

    @Size(max = 120)
    private String signataireAdminNom;

    @Size(max = 120)
    private String signataireTechnicienNom;
}

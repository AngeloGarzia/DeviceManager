package com.devicemanager.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Requête de création ou mise à jour d'une référence MAS.
 */
@Data
public class MasRequest {

    @NotBlank(message = "Le numéro MAS est obligatoire")
    @Size(max = 80, message = "Le numéro MAS ne doit pas dépasser 80 caractères")
    private String numero;

    @Size(max = 80, message = "Le numéro de socle ne doit pas dépasser 80 caractères")
    private String numeroSocle;

    @DecimalMin(value = "0.00", inclusive = true, message = "Le taux de redistribution doit être au moins 0")
    @DecimalMax(value = "100.00", inclusive = true, message = "Le taux de redistribution ne doit pas dépasser 100")
    private BigDecimal tauxRedistribution;

    private LocalDate dateMiseEnService;

    @Size(max = 120, message = "Le type de machine ne doit pas dépasser 120 caractères")
    private String typeMachine;

    @Size(max = 120, message = "Le numéro de série ne doit pas dépasser 120 caractères")
    private String numeroSerie;

    private LocalDate dateCessation;

    @Size(max = 255, message = "La destination ne doit pas dépasser 255 caractères")
    private String destinationMachineUsagee;

    /** Identifiant de la marque du catalogue. */
    @NotNull(message = "La marque MAS est obligatoire")
    private Long marqueId;

    /** Identifiant de la dénomination (optionnel). */
    private Long denoId;

    /**
     * Statut d'exploitation : {@code UTILISEE}, {@code EN_RESERVE}, {@code VENDUE}, {@code DETRUITE}.
     * Si absent, dérivé de {@link #utilise}.
     */
    @Size(max = 40)
    private String statut;

    /** Compatibilité : true = UTILISEE, false = EN_RESERVE (si statut absent). */
    private Boolean utilise;
}

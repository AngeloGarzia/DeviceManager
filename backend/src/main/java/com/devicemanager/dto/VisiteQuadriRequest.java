package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Enregistrement d'une visite quadritrimestrielle SFM × marque.
 */
@Data
public class VisiteQuadriRequest {

    @NotNull(message = "Le SFM est obligatoire")
    private Long sfmId;

    @NotNull(message = "La marque est obligatoire")
    private Long marqueId;

    @NotNull(message = "La date de visite est obligatoire")
    private LocalDate dateVisite;

    @Size(max = 2000, message = "Les notes ne doivent pas dépasser 2000 caractères")
    private String notes;
}

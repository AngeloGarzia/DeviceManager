package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Création / récupération d'une FIT à partir d'une MAS de l'atelier.
 */
@Data
public class FitFromMasRequest {

    @NotNull(message = "La MAS est obligatoire")
    private Long masId;
}

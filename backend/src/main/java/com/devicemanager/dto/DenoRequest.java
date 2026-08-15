package com.devicemanager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Création d'une dénomination dans le référentiel MAS.
 */
@Data
public class DenoRequest {

    @NotNull(message = "La valeur de la dénomination est obligatoire")
    @DecimalMin(value = "0.0001", inclusive = true, message = "La dénomination doit être strictement positive")
    private BigDecimal valeur;

    /** Libellé optionnel ; généré depuis la valeur si absent (ex. {@code 0,01 €}). */
    @Size(max = 40, message = "Le libellé ne doit pas dépasser 40 caractères")
    private String label;
}

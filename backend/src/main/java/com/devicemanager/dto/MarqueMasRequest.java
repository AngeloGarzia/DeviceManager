package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création d'une marque dans le catalogue MAS.
 */
@Data
public class MarqueMasRequest {

    @NotBlank(message = "Le nom de la marque est obligatoire")
    @Size(max = 120, message = "Le nom de la marque ne doit pas dépasser 120 caractères")
    private String label;
}

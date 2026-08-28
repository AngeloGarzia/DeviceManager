package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de mise à jour d'une règle de jeux (libellé / description).
 */
@Data
public class RegleJeuxRequest {

    @NotBlank(message = "Le libellé de la règle de jeux est obligatoire")
    @Size(max = 200, message = "Le libellé ne doit pas dépasser 200 caractères")
    private String label;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;
}

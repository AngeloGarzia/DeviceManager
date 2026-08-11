package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création ou mise à jour d'une référence MAS.
 */
@Data
public class MasRequest {

    @NotBlank(message = "Le numéro MAS est obligatoire")
    @Size(max = 80, message = "Le numéro MAS ne doit pas dépasser 80 caractères")
    private String numero;

    /** Identifiant de la marque du catalogue. */
    @NotNull(message = "La marque MAS est obligatoire")
    private Long marqueId;

    /** Indique si la référence est encore utilisée en exploitation. */
    @NotNull(message = "Indiquez si la MAS est utilisée")
    private Boolean utilise;
}

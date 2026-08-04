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

    @NotBlank
    @Size(max = 80)
    private String numero;

    /** Identifiant de la marque du catalogue. */
    @NotNull
    private Long marqueId;

    /** Indique si la référence est encore utilisée en exploitation. */
    @NotNull
    private Boolean utilise;
}

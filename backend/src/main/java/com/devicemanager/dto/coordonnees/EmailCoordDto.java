package com.devicemanager.dto.coordonnees;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Adresse e-mail dans un bloc de coordonnées atelier.
 */
@Data
public class EmailCoordDto {
    /** Identifiant de l'e-mail existant (mise à jour) ; absent à la création. */
    private Long id;

    @Size(max = 160, message = "L'e-mail ne doit pas dépasser 160 caractères")
    private String valeur;

    /** Indique l'e-mail principal parmi la liste. */
    private boolean principal;
}

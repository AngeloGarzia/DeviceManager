package com.devicemanager.dto.coordonnees;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Numéro de téléphone dans un bloc de coordonnées atelier.
 */
@Data
public class TelephoneCoordDto {
    /** Identifiant du téléphone existant (mise à jour) ; absent à la création. */
    private Long id;

    @Size(max = 40, message = "Le téléphone ne doit pas dépasser 40 caractères")
    private String valeur;

    /** Libellé optionnel (ex. {@code Fixe}, {@code Mobile}). */
    @Size(max = 40, message = "Le libellé du téléphone ne doit pas dépasser 40 caractères")
    private String label;

    /** Indique le numéro principal parmi la liste. */
    private boolean principal;
}

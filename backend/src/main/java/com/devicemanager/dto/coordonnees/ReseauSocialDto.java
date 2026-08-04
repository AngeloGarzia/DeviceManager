package com.devicemanager.dto.coordonnees;

import com.devicemanager.entity.coordonnees.TypeReseauSocial;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Lien vers un réseau social dans un bloc de coordonnées atelier.
 */
@Data
public class ReseauSocialDto {
    /** Identifiant du lien existant (mise à jour) ; absent à la création. */
    private Long id;
    private TypeReseauSocial type;

    @Size(max = 255)
    private String url;
}

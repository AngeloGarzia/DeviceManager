package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Utilisateur sélectionnable comme signataire FIT.
 */
@Data
@Builder
public class FitSignataireDto {
    private Long id;
    private String username;
    private String nom;
    private String prenom;
    /** Prénom + nom, ou username si absents. */
    private String displayName;
    private String role;
}

package com.devicemanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création ou mise à jour d'un compte utilisateur.
 */
@Data
public class UserRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @NotBlank
    @Size(max = 80)
    private String nom;

    @NotBlank
    @Size(max = 80)
    private String prenom;

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    /** Mot de passe ; omis lors d'une mise à jour sans changement. */
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(max = 30)
    private String role;

    /** Obligatoire pour TECHNICIEN ; optionnel pour ADMIN. */
    private Long preferredAtelierId;
}

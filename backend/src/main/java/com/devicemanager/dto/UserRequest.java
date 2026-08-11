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

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(max = 80, message = "Le nom d'utilisateur ne doit pas dépasser 80 caractères")
    private String username;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 80, message = "Le nom ne doit pas dépasser 80 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 80, message = "Le prénom ne doit pas dépasser 80 caractères")
    private String prenom;

    @NotBlank(message = "L'e-mail est obligatoire")
    @Email(message = "L'e-mail est invalide")
    @Size(max = 160, message = "L'e-mail ne doit pas dépasser 160 caractères")
    private String email;

    /** Mot de passe ; omis lors d'une mise à jour sans changement. */
    @Size(min = 6, max = 100, message = "Le mot de passe doit contenir entre 6 et 100 caractères")
    private String password;

    @NotBlank(message = "Le rôle est obligatoire")
    @Size(max = 30, message = "Le rôle est invalide")
    private String role;

    /** Obligatoire pour TECHNICIEN ; optionnel pour ADMIN. */
    private Long preferredAtelierId;
}

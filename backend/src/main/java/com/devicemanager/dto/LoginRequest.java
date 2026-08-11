package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de connexion utilisateur.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(max = 80, message = "Le nom d'utilisateur ne doit pas dépasser 80 caractères")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 4, max = 100, message = "Le mot de passe doit contenir entre 4 et 100 caractères")
    private String password;
}

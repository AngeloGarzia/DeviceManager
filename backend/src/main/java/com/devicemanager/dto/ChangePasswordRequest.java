package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de changement de mot de passe (utilisateur authentifié).
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    @Size(min = 4, max = 100, message = "Le mot de passe actuel est invalide")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit contenir entre 8 et 100 caractères")
    private String newPassword;
}

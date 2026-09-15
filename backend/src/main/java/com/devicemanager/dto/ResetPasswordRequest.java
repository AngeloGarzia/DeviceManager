package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Réinitialisation du mot de passe via jeton reçu par e-mail.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Le jeton est obligatoire")
    @Size(max = 128, message = "Le jeton est invalide")
    private String token;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit contenir entre 8 et 100 caractères")
    private String newPassword;
}

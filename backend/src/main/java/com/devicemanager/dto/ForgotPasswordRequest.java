package com.devicemanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Demande de lien de réinitialisation de mot de passe.
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "L'e-mail est obligatoire")
    @Email(message = "L'e-mail est invalide")
    @Size(max = 160, message = "L'e-mail ne doit pas dépasser 160 caractères")
    private String email;
}

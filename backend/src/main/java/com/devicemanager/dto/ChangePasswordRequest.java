package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de changement de mot de passe (utilisateur authentifié).
 */
@Data
public class ChangePasswordRequest {

    @NotBlank
    @Size(min = 4, max = 100)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}

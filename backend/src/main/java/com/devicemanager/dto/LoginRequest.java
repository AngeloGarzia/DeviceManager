package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de connexion utilisateur.
 */
@Data
public class LoginRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @NotBlank
    @Size(min = 4, max = 100)
    private String password;
}

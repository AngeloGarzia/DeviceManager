package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création / mise à jour d'un casino.
 */
@Data
public class CasinoRequest {

    @NotBlank
    @Size(max = 120)
    private String nom;
}

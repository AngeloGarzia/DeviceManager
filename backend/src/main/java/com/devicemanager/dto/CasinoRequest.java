package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création / mise à jour d'un casino.
 */
@Data
public class CasinoRequest {

    @NotBlank(message = "Le nom du casino est obligatoire")
    @Size(max = 120, message = "Le nom du casino ne doit pas dépasser 120 caractères")
    private String nom;
}

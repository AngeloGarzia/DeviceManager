package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requête de création d'une marque dans le catalogue MAS.
 */
@Data
public class MarqueMasRequest {

    @NotBlank
    @Size(max = 120)
    private String label;
}

package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArretMaintenanceRequest {

    @NotNull
    private Long masId;

    @NotBlank
    @Size(max = 500)
    private String motifArret;

    private LocalDateTime dateHeureArret;

    private boolean registreTechniqueAJour;

    /** Obligatoire si {@link #registreTechniqueAJour}. */
    private String signatureArret;

    @Size(max = 120)
    private String signataireArretNom;
}

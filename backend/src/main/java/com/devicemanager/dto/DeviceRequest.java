package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Requête de création ou mise à jour d'une pièce ({@link com.devicemanager.entity.Device}).
 */
@Data
public class DeviceRequest {

    @NotBlank
    @Size(max = 120)
    private String nom;

    @Size(max = 80)
    private String reference;

    @NotBlank
    @Size(max = 500)
    private String usage;

    @NotNull
    private LocalDate dateAcquisition;

    @NotNull
    private Boolean obsolete;

    /** Identifiant SFM associé (optionnel). */
    private Long sfmId;

    /** Identifiant MAS associé (optionnel) — la marque est héritée de la MAS si renseignée. */
    private Long masId;

    /** Identifiants des photos existantes à conserver lors d'une mise à jour. */
    private List<Long> keepPhotoIds = new ArrayList<>();
}

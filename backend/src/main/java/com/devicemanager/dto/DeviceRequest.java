package com.devicemanager.dto;

import jakarta.validation.constraints.Min;
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

    @NotBlank(message = "Le nom de la pièce est obligatoire")
    @Size(max = 120, message = "Le nom de la pièce ne doit pas dépasser 120 caractères")
    private String nom;

    @Size(max = 80, message = "La référence ne doit pas dépasser 80 caractères")
    private String reference;

    @NotBlank(message = "L'usage de la pièce est obligatoire")
    @Size(max = 500, message = "L'usage ne doit pas dépasser 500 caractères")
    private String usage;

    /** Informations techniques (manuel / datasheet / notice). */
    @Size(max = 8000, message = "Les informations techniques ne doivent pas dépasser 8000 caractères")
    private String informationTechnique;

    @NotNull(message = "La date d'acquisition est obligatoire")
    private LocalDate dateAcquisition;

    @NotNull(message = "Indiquez si la pièce est obsolète")
    private Boolean obsolete;

    /** Quantité en stock (≥ 0). Défaut 0 si absent. */
    @Min(value = 0, message = "Le stock ne peut pas être négatif")
    private Integer stock;

    /** Identifiant SFM associé (optionnel). */
    private Long sfmId;

    /** Identifiant MAS associé (optionnel) — la marque est héritée de la MAS si renseignée. */
    private Long masId;

    /** Identifiants des photos existantes à conserver lors d'une mise à jour. */
    private List<Long> keepPhotoIds = new ArrayList<>();

    /** Identifiants des documents PDF existants à conserver lors d'une mise à jour. */
    private List<Long> keepDocumentIds = new ArrayList<>();

    /**
     * Types des nouveaux PDF uploadés ({@code MANUAL}, {@code DATASHEET}, {@code NOTICE}),
     * dans le même ordre que les fichiers {@code documents} du multipart.
     */
    private List<String> newDocumentTypes = new ArrayList<>();
}

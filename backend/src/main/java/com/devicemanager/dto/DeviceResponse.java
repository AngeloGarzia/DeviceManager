package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Représentation complète d'une pièce renvoyée par l'API.
 */
@Data
@Builder
public class DeviceResponse {
    private Long id;
    private String nom;
    private String reference;
    private String usage;
    private LocalDate dateAcquisition;
    private boolean obsolete;
    /** URL de la photo principale (compatibilité listes et commandes). */
    private String photoUrl;
    private String contentType;
    private Long fileSize;
    private List<DevicePhotoResponse> photos;
    private Long sfmId;
    private String sfmNom;
    private Long masId;
    private String masNumero;
    /** Code court de la marque MAS. */
    private String masMarque;
    private Long marqueId;
    /** Code court de la marque de la pièce. */
    private String marque;
    /** Libellé complet de la marque de la pièce. */
    private String marqueLabel;
}

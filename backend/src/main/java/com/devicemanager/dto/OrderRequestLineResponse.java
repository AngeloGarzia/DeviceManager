package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ligne détaillée d'une commande renvoyée par l'API.
 */
@Data
@Builder
public class OrderRequestLineResponse {
    private Long id;
    private Long deviceId;
    private String pieceNom;
    private String reference;
    private Integer quantite;
    private String photoUrl;
    private Long sfmId;
    private String sfmNom;
}

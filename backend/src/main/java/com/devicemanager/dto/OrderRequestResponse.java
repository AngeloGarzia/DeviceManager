package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Représentation d'une demande de commande renvoyée par l'API.
 */
@Data
@Builder
public class OrderRequestResponse {
    private Long id;
    private String requestedBy;
    private String technicienNom;
    private String message;
    /** Statut du cycle de vie (ex. {@code PENDING}, {@code VALIDATED}). */
    private String status;
    private LocalDateTime dateDemande;
    /** Alias de {@link #dateDemande} pour compatibilité front. */
    private LocalDateTime createdAt;
    private Integer totalPieces;
    private Integer totalQuantite;
    private List<OrderRequestLineResponse> lignes;

    /** Champs dénormalisés de la 1re ligne pour l'affichage en liste. */
    private String pieceNom;
    private String reference;
    private Integer quantite;
    private Long deviceId;
    private String photoUrl;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderRequestResponse {
    private Long id;
    private String requestedBy;
    private String technicienNom;
    private String message;
    private String status;
    private LocalDateTime dateDemande;
    private LocalDateTime createdAt;
    private Integer totalPieces;
    private Integer totalQuantite;
    private List<OrderRequestLineResponse> lignes;

    /** Compat affichage liste (1re pièce). */
    private String pieceNom;
    private String reference;
    private Integer quantite;
    private Long deviceId;
    private String photoUrl;
}

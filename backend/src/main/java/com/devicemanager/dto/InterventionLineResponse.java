package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ligne archivée d'un bon d'intervention.
 */
@Data
@Builder
public class InterventionLineResponse {

    private Long id;
    private Long deviceId;
    private String pieceNom;
    private String pieceReference;
    private Integer quantite;
    private Integer stockAvant;
    private Integer stockApres;
    private String photoUrl;
}

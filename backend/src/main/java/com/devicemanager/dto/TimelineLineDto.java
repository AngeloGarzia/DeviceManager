package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ligne détaillée d'un événement de timeline.
 */
@Data
@Builder
public class TimelineLineDto {
    private Long deviceId;
    private String pieceNom;
    private String pieceReference;
    private Integer quantite;
    private Integer stockAvant;
    private Integer stockApres;
    private Integer delta;
}

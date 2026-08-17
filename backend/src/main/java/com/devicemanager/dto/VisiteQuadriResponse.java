package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Visite quadritrimestrielle enregistrée.
 */
@Data
@Builder
public class VisiteQuadriResponse {
    private Long id;
    private Long sfmId;
    private String sfmNom;
    private Long marqueId;
    private String marqueLabel;
    private LocalDate dateVisite;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
}

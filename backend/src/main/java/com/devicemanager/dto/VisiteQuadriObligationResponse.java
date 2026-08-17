package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Obligation de visite quadritrimestrielle pour un couple SFM × marque.
 * {@code level} : {@code OK}, {@code WARN} (échéance ≤ 7 j), {@code OVERDUE} (en retard ou jamais visitée).
 */
@Data
@Builder
public class VisiteQuadriObligationResponse {
    private Long sfmId;
    private String sfmNom;
    private Long marqueId;
    private String marqueLabel;
    private LocalDate lastVisitDate;
    private LocalDate dueDate;
    private Long daysRemaining;
    private String level;
}

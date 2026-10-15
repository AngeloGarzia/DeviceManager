package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Statut de nettoyage d'une MAS (dernière date, fraîcheur 0–100).
 */
@Data
@Builder
public class TodoNettoyageMasStatut {

    private Long masId;
    private String masNumero;
    private String marqueLabel;
    private String masStatut;
    private LocalDateTime dernierNettoyageAt;
    /** null si jamais nettoyée. */
    private Integer joursDepuis;
    private long nettoyageCount;
    /** 100 = nettoyée aujourd'hui ; 0 = jamais ou ≥ seuil critique. */
    private int freshnessScore;
    /** RECENT | OK | ATTENTION | URGENT | JAMAIS */
    private String niveau;
}

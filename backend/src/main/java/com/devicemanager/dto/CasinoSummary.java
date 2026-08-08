package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Vue synthétique d'un casino avec son groupe parent.
 */
@Data
@Builder
public class CasinoSummary {
    private Long id;
    private String nom;
    private Long groupeId;
    private String groupeNom;
    /** Nombre d'ateliers rattachés (structure casino → atelier). */
    private long atelierCount;
}

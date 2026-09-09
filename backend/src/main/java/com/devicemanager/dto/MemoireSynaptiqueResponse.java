package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue exposée de la mémoire synaptique de l'atelier courant.
 */
@Data
@Builder
public class MemoireSynaptiqueResponse {

    private Long atelierId;
    private String atelierNom;
    private String overview;
    private List<String> recentFacts;
    private String lastEventType;
    private LocalDateTime lastEventAt;
    private LocalDateTime updatedAt;
}

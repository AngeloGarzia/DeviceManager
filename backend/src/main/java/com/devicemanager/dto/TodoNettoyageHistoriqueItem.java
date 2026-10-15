package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entrée d'historique de nettoyage pour une MAS.
 */
@Data
@Builder
public class TodoNettoyageHistoriqueItem {

    private Long todoId;
    private String todoTitre;
    private String todoStatut;
    private Long masId;
    private String masNumero;
    private LocalDateTime cocheAt;
    private String cocheByDisplayName;
    private LocalDateTime completedAt;
    private String signataireClotureNom;
}

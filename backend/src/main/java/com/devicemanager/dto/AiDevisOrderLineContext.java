package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Contexte d'une ligne de commande pour l'analyse IA d'un devis.
 */
@Data
@Builder
public class AiDevisOrderLineContext {
    private Long deviceId;
    private String nom;
    private String reference;
}

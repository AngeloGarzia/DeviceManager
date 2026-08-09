package com.devicemanager.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Modèle IA découvert en ligne chez un fournisseur.
 */
@Value
@Builder
public class AiModelOption {
    String id;
    String label;
    boolean vision;
}

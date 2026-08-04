package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * État de disponibilité d'un fournisseur de modèle IA (OpenAI, Anthropic, etc.).
 */
@Data
@Builder
public class AiProviderAvailability {
    /** Identifiant technique du fournisseur. */
    private String id;
    /** Libellé affiché. */
    private String label;
    /** Indique si une clé API est configurée pour ce fournisseur. */
    private boolean hasApiKey;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Liste des modèles IA disponibles en ligne pour un fournisseur.
 */
@Value
@Builder
public class AiModelsResponse {
    String providerId;
    String providerLabel;
    boolean hasApiKey;
    /** Message d'erreur ou d'info (clé absente, échec API, etc.). */
    String message;
    List<AiModelOption> models;
}

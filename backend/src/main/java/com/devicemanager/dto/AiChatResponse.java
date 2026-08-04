package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Réponse de l'assistant IA avec état de disponibilité des fournisseurs.
 */
@Data
@Builder
public class AiChatResponse {
    private String reply;
    /** Indique si l'assistant IA est activé globalement. */
    private boolean enabled;
    /** État de configuration de chaque fournisseur IA. */
    private List<AiProviderAvailability> providers;
}

package com.devicemanager.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Batterie de clés API IA injectées depuis les variables d'environnement
 * ({@code GEMINI_API_KEY}, {@code OPENAI_API_KEY}, etc.).
 * <p>
 * Fournit l'accès aux clés par identifiant de fournisseur et aide au seed initial
 * de la configuration IA lors du setup.
 */
@Component
public class AiApiKeyBattery {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${OPENAI_API_KEY:}")
    private String openaiApiKey;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    @Value("${MISTRAL_API_KEY:}")
    private String mistralApiKey;

    @Value("${OPENROUTER_API_KEY:}")
    private String openrouterApiKey;

    @Value("${DEEPSEEK_API_KEY:}")
    private String deepseekApiKey;

    @Value("${TOGETHER_API_KEY:}")
    private String togetherApiKey;

    @Value("${FIREWORKS_API_KEY:}")
    private String fireworksApiKey;

    /**
     * Retourne la clé API associée au fournisseur demandé, ou une chaîne vide si absente.
     *
     * @param providerId identifiant brut du fournisseur (normalisé via {@link AiProviders#normalizeProvider})
     * @return clé API trimée, ou {@code ""} si inconnue ou non configurée
     */
    public String keyFor(String providerId) {
        String id = AiProviders.normalizeProvider(providerId);
        String key = switch (id) {
            case "gemini" -> geminiApiKey;
            case "openai" -> openaiApiKey;
            case "groq" -> groqApiKey;
            case "mistral" -> mistralApiKey;
            case "openrouter" -> openrouterApiKey;
            case "deepseek" -> deepseekApiKey;
            case "together" -> togetherApiKey;
            case "fireworks" -> fireworksApiKey;
            default -> "";
        };
        return blankToEmpty(key);
    }

    /**
     * Nom de la variable d'environnement attendue pour un fournisseur donné.
     *
     * @param providerId identifiant du fournisseur
     * @return nom de variable (ex. {@code OPENAI_API_KEY}), ou {@code OPENAI_API_KEY} par défaut
     */
    public static String envVarName(String providerId) {
        return switch (AiProviders.normalizeProvider(providerId)) {
            case "gemini" -> "GEMINI_API_KEY";
            case "openai" -> "OPENAI_API_KEY";
            case "groq" -> "GROQ_API_KEY";
            case "mistral" -> "MISTRAL_API_KEY";
            case "openrouter" -> "OPENROUTER_API_KEY";
            case "deepseek" -> "DEEPSEEK_API_KEY";
            case "together" -> "TOGETHER_API_KEY";
            case "fireworks" -> "FIREWORKS_API_KEY";
            default -> "OPENAI_API_KEY";
        };
    }

    /**
     * Première clé non vide utile pour le seed du setup : fournisseur préféré d'abord,
     * puis parcours de tous les fournisseurs connus.
     *
     * @param preferredProviderId fournisseur à tenter en priorité
     * @return clé API non vide, ou {@code ""} si aucune n'est configurée
     */
    public String seedKey(String preferredProviderId) {
        String preferred = keyFor(preferredProviderId);
        if (!preferred.isBlank()) {
            return preferred;
        }
        for (String id : AiProviders.providerIds()) {
            String k = keyFor(id);
            if (!k.isBlank()) {
                return k;
            }
        }
        return "";
    }

    /**
     * Indique si au moins une clé API IA est configurée dans l'environnement.
     *
     * @return {@code true} si une clé exploitable existe
     */
    public boolean hasAnyKey() {
        return !seedKey("openai").isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

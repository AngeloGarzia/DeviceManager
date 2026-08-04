package com.devicemanager.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Batterie de clés API IA lues depuis le .env (une clé par fournisseur).
 * Priorité à l'exécution : clé env du fournisseur courant, sinon AI_API_KEY (Setup).
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

    /** Première clé non vide utile pour le seed Setup (provider par défaut d'abord). */
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

    public boolean hasAnyKey() {
        return !seedKey("openai").isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

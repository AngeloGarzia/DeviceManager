package com.devicemanager.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogue des fournisseurs IA (identifiants et URL de base uniquement).
 * <p>
 * Les modèles ne sont plus listés en dur : ils sont découverts en ligne
 * via {@link com.devicemanager.service.AiModelDiscoveryService}.
 */
public final class AiProviders {

    /**
     * Métadonnées d'un fournisseur IA.
     *
     * @param id      identifiant normalisé (ex. {@code openai}, {@code gemini})
     * @param label   libellé affiché à l'utilisateur
     * @param baseUrl URL de base compatible OpenAI ({@code /v1/models}, {@code /v1/chat/completions})
     */
    public record Provider(
            String id,
            String label,
            String baseUrl
    ) {
    }

    private static final List<Provider> ALL = List.of(
            new Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
            new Provider("openai", "OpenAI", "https://api.openai.com"),
            new Provider("groq", "Groq", "https://api.groq.com/openai"),
            new Provider("mistral", "Mistral AI", "https://api.mistral.ai"),
            new Provider("openrouter", "OpenRouter (multi-IA)", "https://openrouter.ai/api"),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com"),
            new Provider("together", "Together AI", "https://api.together.xyz"),
            new Provider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference")
    );

    private AiProviders() {
    }

    /** @return liste immuable de tous les fournisseurs configurés */
    public static List<Provider> all() {
        return ALL;
    }

    /**
     * Recherche un fournisseur par identifiant ; retourne le premier si l'identifiant est vide.
     */
    public static Optional<Provider> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.of(ALL.getFirst());
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(p -> p.id().equals(key)).findFirst();
    }

    /** Retourne le fournisseur demandé, ou le premier disponible en secours. */
    public static Provider require(String id) {
        return find(id).orElse(ALL.getFirst());
    }

    /**
     * Indique si le modèle accepte probablement des entrées image (heuristique sur le nom).
     */
    public static boolean supportsVision(String providerId, String modelId) {
        String m = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        String p = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT);
        if (p.equals("gemini")) {
            return !m.contains("embedding") && !m.contains("tts") && !m.contains("image-generation");
        }
        return m.contains("vision")
                || m.contains("gpt-4o")
                || m.contains("gpt-4.1")
                || m.contains("gpt-4-turbo")
                || m.contains("pixtral")
                || m.contains("gemini")
                || m.contains("claude")
                || m.contains("llava")
                || m.contains("qwen2-vl")
                || m.contains("qwen2.5-vl")
                || m.contains("llama-4")
                || m.contains("scout");
    }

    /**
     * @param id identifiant du fournisseur
     * @return {@code true} si le fournisseur figure dans le catalogue
     */
    public static boolean isKnownProvider(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().anyMatch(p -> p.id().equals(key));
    }

    /** @return identifiants de tous les fournisseurs du catalogue */
    public static List<String> providerIds() {
        return ALL.stream().map(Provider::id).toList();
    }

    /**
     * Normalise un identifiant de fournisseur ; retourne {@code openai} si inconnu ou vide.
     */
    public static String normalizeProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return "openai";
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (Arrays.asList(
                "gemini", "openai", "groq", "mistral", "openrouter", "deepseek", "together", "fireworks"
        ).contains(id)) {
            return id;
        }
        return "openai";
    }
}

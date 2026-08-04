package com.devicemanager.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogue des fournisseurs IA compatibles API OpenAI (URL de base et modèles supportés).
 * <p>
 * Utilisé pour la configuration du chat, le scan d'étiquettes et l'exposition des options
 * disponibles au frontend.
 */
public final class AiProviders {

    /**
     * Métadonnées d'un fournisseur IA (identifiant, libellé, URL de base, modèles).
     *
     * @param id      identifiant normalisé (ex. {@code openai}, {@code gemini})
     * @param label   libellé affiché à l'utilisateur
     * @param baseUrl URL de base compatible OpenAI
     * @param models  modèles proposés pour ce fournisseur
     */
    public record Provider(
            String id,
            String label,
            String baseUrl,
            List<Model> models
    ) {
    }

    /**
     * Modèle IA proposé par un fournisseur.
     *
     * @param id     identifiant technique du modèle
     * @param label  libellé affiché
     * @param vision {@code true} si le modèle accepte des entrées image
     */
    public record Model(String id, String label, boolean vision) {
    }

    private static final List<Provider> ALL = List.of(
            new Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", List.of(
                    new Model("gemini-2.0-flash", "Gemini 2.0 Flash (vision)", true),
                    new Model("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite (vision)", true),
                    new Model("gemini-2.5-flash", "Gemini 2.5 Flash (vision)", true),
                    new Model("gemini-2.5-pro", "Gemini 2.5 Pro (vision)", true),
                    new Model("gemini-1.5-flash", "Gemini 1.5 Flash (vision)", true),
                    new Model("gemini-1.5-pro", "Gemini 1.5 Pro (vision)", true)
            )),
            new Provider("openai", "OpenAI", "https://api.openai.com", List.of(
                    new Model("gpt-4o-mini", "gpt-4o-mini — économique (vision)", true),
                    new Model("gpt-4o", "gpt-4o — polyvalent (vision)", true),
                    new Model("gpt-4.1-mini", "gpt-4.1-mini", true),
                    new Model("gpt-4.1", "gpt-4.1", true),
                    new Model("gpt-4.1-nano", "gpt-4.1-nano", false),
                    new Model("o4-mini", "o4-mini — raisonnement", false),
                    new Model("o3-mini", "o3-mini — raisonnement", false),
                    new Model("gpt-4-turbo", "gpt-4-turbo", true),
                    new Model("gpt-3.5-turbo", "gpt-3.5-turbo", false),
                    new Model("chatgpt-4o-latest", "chatgpt-4o-latest", true)
            )),
            new Provider("groq", "Groq", "https://api.groq.com/openai", List.of(
                    new Model("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", false),
                    new Model("llama-3.1-8b-instant", "Llama 3.1 8B Instant", false),
                    new Model("openai/gpt-oss-120b", "GPT-OSS 120B", false),
                    new Model("openai/gpt-oss-20b", "GPT-OSS 20B", false),
                    new Model("qwen/qwen3.6-27b", "Qwen3.6 27B", false),
                    new Model("groq/compound", "Groq Compound", false)
                    // Pas de modèle vision sur la plupart des comptes Groq (Llama 4 Scout souvent indisponible)
            )),
            new Provider("mistral", "Mistral AI", "https://api.mistral.ai", List.of(
                    new Model("mistral-small-latest", "Mistral Small", false),
                    new Model("mistral-medium-latest", "Mistral Medium", false),
                    new Model("mistral-large-latest", "Mistral Large", false),
                    new Model("open-mistral-nemo", "Mistral Nemo", false),
                    new Model("codestral-latest", "Codestral", false),
                    new Model("pixtral-12b-2409", "Pixtral 12B (vision)", true),
                    new Model("pixtral-large-latest", "Pixtral Large (vision)", true)
            )),
            new Provider("openrouter", "OpenRouter (multi-IA)", "https://openrouter.ai/api", List.of(
                    new Model("openai/gpt-4o-mini", "OpenAI GPT-4o mini (vision)", true),
                    new Model("openai/gpt-4o", "OpenAI GPT-4o (vision)", true),
                    new Model("anthropic/claude-3.5-sonnet", "Anthropic Claude 3.5 Sonnet", true),
                    new Model("anthropic/claude-sonnet-4", "Anthropic Claude Sonnet 4", true),
                    new Model("google/gemini-2.0-flash-001", "Google Gemini 2.0 Flash (vision)", true),
                    new Model("google/gemini-2.5-pro-preview", "Google Gemini 2.5 Pro", true),
                    new Model("meta-llama/llama-3.3-70b-instruct", "Meta Llama 3.3 70B", false),
                    new Model("mistralai/mistral-large", "Mistral Large", false),
                    new Model("deepseek/deepseek-chat", "DeepSeek Chat", false),
                    new Model("qwen/qwen-2.5-72b-instruct", "Qwen 2.5 72B", false)
            )),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com", List.of(
                    new Model("deepseek-chat", "DeepSeek Chat", false),
                    new Model("deepseek-reasoner", "DeepSeek Reasoner", false)
            )),
            new Provider("together", "Together AI", "https://api.together.xyz", List.of(
                    new Model("meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo", "Llama 3.1 70B Turbo", false),
                    new Model("meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo", "Llama 3.1 8B Turbo", false),
                    new Model("mistralai/Mixtral-8x7B-Instruct-v0.1", "Mixtral 8x7B", false),
                    new Model("Qwen/Qwen2.5-72B-Instruct-Turbo", "Qwen2.5 72B Turbo", false),
                    new Model("meta-llama/Llama-Vision-Free", "Llama Vision Free", true)
            )),
            new Provider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference", List.of(
                    new Model("accounts/fireworks/models/llama-v3p3-70b-instruct", "Llama 3.3 70B", false),
                    new Model("accounts/fireworks/models/llama-v3p1-8b-instruct", "Llama 3.1 8B", false),
                    new Model("accounts/fireworks/models/mixtral-8x22b-instruct", "Mixtral 8x22B", false),
                    new Model("accounts/fireworks/models/qwen2p5-72b-instruct", "Qwen2.5 72B", false)
            ))
    );

    private AiProviders() {
    }

    /** @return liste immuable de tous les fournisseurs configurés */
    public static List<Provider> all() {
        return ALL;
    }

    /**
     * Recherche un fournisseur par identifiant ; retourne le premier si l'identifiant est vide.
     *
     * @param id identifiant du fournisseur (insensible à la casse)
     * @return fournisseur correspondant, ou le premier de la liste par défaut
     */
    public static Optional<Provider> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.of(ALL.getFirst());
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(p -> p.id().equals(key)).findFirst();
    }

    /**
     * Retourne le fournisseur demandé, ou le premier disponible en secours.
     *
     * @param id identifiant du fournisseur
     * @return fournisseur résolu (jamais vide grâce au fallback)
     */
    public static Provider require(String id) {
        return find(id).orElse(ALL.getFirst());
    }

    /**
     * Modèle par défaut pour un fournisseur (premier de la liste, ou {@code gpt-4o-mini}).
     *
     * @param providerId identifiant du fournisseur
     * @return identifiant de modèle par défaut
     */
    public static String defaultModel(String providerId) {
        Provider p = require(providerId);
        return p.models().isEmpty() ? "gpt-4o-mini" : p.models().getFirst().id();
    }

    /**
     * Indique si le modèle accepte des entrées visuelles (catalogue ou heuristique sur le nom).
     *
     * @param providerId identifiant du fournisseur
     * @param modelId    identifiant du modèle
     * @return {@code true} si la vision est supportée
     */
    public static boolean supportsVision(String providerId, String modelId) {
        return find(providerId)
                .flatMap(p -> p.models().stream().filter(m -> m.id().equals(modelId)).findFirst())
                .map(Model::vision)
                .orElseGet(() -> {
                    String m = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
                    return m.contains("vision") || m.contains("gpt-4o") || m.contains("pixtral")
                            || m.contains("gemini") || m.contains("claude");
                });
    }

    /**
     * Premier modèle vision disponible pour le fournisseur, ou {@code null} si aucun.
     *
     * @param providerId identifiant du fournisseur
     * @return identifiant d'un modèle vision, ou {@code null}
     */
    public static String visionFallbackModel(String providerId) {
        Provider p = require(providerId);
        return p.models().stream()
                .filter(Model::vision)
                .map(Model::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * @param id identifiant du fournisseur
     * @return {@code true} si le fournisseur figure dans le catalogue
     */
    public static boolean isKnownProvider(String id) {
        return find(id).isPresent();
    }

    /** @return identifiants de tous les fournisseurs du catalogue */
    public static List<String> providerIds() {
        return ALL.stream().map(Provider::id).toList();
    }

    /**
     * Vérifie si un modèle existe dans au moins un fournisseur du catalogue.
     *
     * @param modelId identifiant du modèle
     * @return {@code true} si le modèle est connu
     */
    public static boolean isKnownModelAcrossProviders(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        return ALL.stream().anyMatch(p -> p.models().stream().anyMatch(m -> m.id().equals(modelId.trim())));
    }

    /**
     * Déduit le fournisseur à partir de l'identifiant d'un modèle connu.
     *
     * @param modelId identifiant du modèle
     * @return identifiant du fournisseur, ou vide si le modèle est inconnu
     */
    public static Optional<String> inferProviderFromModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        String id = modelId.trim();
        return ALL.stream()
                .filter(p -> p.models().stream().anyMatch(m -> m.id().equals(id)))
                .map(Provider::id)
                .findFirst();
    }

    /**
     * Normalise un identifiant de fournisseur ; retourne {@code openai} si inconnu ou vide.
     *
     * @param raw identifiant brut saisi ou lu en configuration
     * @return identifiant normalisé en minuscules
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

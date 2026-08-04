package com.devicemanager.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fournisseurs compatibles OpenAI (baseUrl + modèles courants).
 */
public final class AiProviders {

    public record Provider(
            String id,
            String label,
            String baseUrl,
            List<Model> models
    ) {
    }

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
                    new Model("llama-3.1-70b-versatile", "Llama 3.1 70B Versatile", false),
                    new Model("gemma2-9b-it", "Gemma2 9B IT", false),
                    new Model("mixtral-8x7b-32768", "Mixtral 8x7B", false),
                    new Model("qwen/qwen3-32b", "Qwen3 32B", false),
                    new Model("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout (vision)", true),
                    new Model("meta-llama/llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick (vision)", true)
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

    public static List<Provider> all() {
        return ALL;
    }

    public static Optional<Provider> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.of(ALL.getFirst());
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(p -> p.id().equals(key)).findFirst();
    }

    public static Provider require(String id) {
        return find(id).orElse(ALL.getFirst());
    }

    public static String defaultModel(String providerId) {
        Provider p = require(providerId);
        return p.models().isEmpty() ? "gpt-4o-mini" : p.models().getFirst().id();
    }

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

    public static String visionFallbackModel(String providerId) {
        Provider p = require(providerId);
        return p.models().stream()
                .filter(Model::vision)
                .map(Model::id)
                .findFirst()
                .orElse(null);
    }

    public static boolean isKnownProvider(String id) {
        return find(id).isPresent();
    }

    public static List<String> providerIds() {
        return ALL.stream().map(Provider::id).toList();
    }

    public static boolean isKnownModelAcrossProviders(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        return ALL.stream().anyMatch(p -> p.models().stream().anyMatch(m -> m.id().equals(modelId.trim())));
    }

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

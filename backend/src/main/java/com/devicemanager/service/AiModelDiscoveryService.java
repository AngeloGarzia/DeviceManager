package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.ai.AiProviders;
import com.devicemanager.dto.AiModelOption;
import com.devicemanager.dto.AiModelsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Découvre en ligne les modèles chat disponibles chez chaque fournisseur IA
 * (pas de catalogue de modèles en dur).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelDiscoveryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final AiApiKeyBattery aiApiKeyBattery;
    private final ObjectMapper objectMapper;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Liste les modèles online pour un fournisseur (cache court).
     */
    public AiModelsResponse listModels(String providerId) {
        String id = AiProviders.normalizeProvider(providerId);
        AiProviders.Provider provider = AiProviders.require(id);
        String apiKey = aiApiKeyBattery.keyFor(id);
        if (apiKey == null || apiKey.isBlank()) {
            return AiModelsResponse.builder()
                    .providerId(id)
                    .providerLabel(provider.label())
                    .hasApiKey(false)
                    .message("Clé API absente (" + AiApiKeyBattery.envVarName(id) + ").")
                    .models(List.of())
                    .build();
        }

        CacheEntry cached = cache.get(id);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        try {
            List<AiModelOption> models = "gemini".equals(id)
                    ? fetchGeminiModels(apiKey)
                    : fetchOpenAiCompatibleModels(provider, apiKey);
            models = sortModels(models);
            AiModelsResponse response = AiModelsResponse.builder()
                    .providerId(id)
                    .providerLabel(provider.label())
                    .hasApiKey(true)
                    .message(models.isEmpty()
                            ? "Aucun modèle chat renvoyé par le fournisseur."
                            : models.size() + " modèle(s) disponible(s).")
                    .models(models)
                    .build();
            cache.put(id, new CacheEntry(Instant.now().plus(CACHE_TTL), response));
            return response;
        } catch (RestClientResponseException ex) {
            log.warn("Découverte modèles IA échouée ({}): {} {}", id, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            String msg = friendlyFetchError(id, ex);
            return AiModelsResponse.builder()
                    .providerId(id)
                    .providerLabel(provider.label())
                    .hasApiKey(true)
                    .message(msg)
                    .models(List.of())
                    .build();
        } catch (Exception ex) {
            log.warn("Découverte modèles IA échouée ({}): {}", id, ex.getMessage());
            return AiModelsResponse.builder()
                    .providerId(id)
                    .providerLabel(provider.label())
                    .hasApiKey(true)
                    .message("Impossible de récupérer les modèles : " + ex.getMessage())
                    .models(List.of())
                    .build();
        }
    }

    /** Premier modèle vision découvert en ligne, ou vide. */
    public String firstVisionModelId(String providerId) {
        return listModels(providerId).getModels().stream()
                .filter(AiModelOption::isVision)
                .map(AiModelOption::getId)
                .findFirst()
                .orElse(null);
    }

    /** Premier modèle chat découvert, ou vide. */
    public String firstModelId(String providerId) {
        return listModels(providerId).getModels().stream()
                .map(AiModelOption::getId)
                .findFirst()
                .orElse(null);
    }

    private List<AiModelOption> fetchGeminiModels(String apiKey) throws Exception {
        List<AiModelOption> out = new ArrayList<>();
        String pageToken = null;
        do {
            String uri = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100";
            if (pageToken != null && !pageToken.isBlank()) {
                uri += "&pageToken=" + java.net.URLEncoder.encode(pageToken, java.nio.charset.StandardCharsets.UTF_8);
            }
            String body = RestClient.create()
                    .get()
                    .uri(uri)
                    .header("x-goog-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode models = root.path("models");
            if (models.isArray()) {
                for (JsonNode node : models) {
                    String name = text(node, "name");
                    if (name == null) {
                        continue;
                    }
                    String id = name.startsWith("models/") ? name.substring("models/".length()) : name;
                    // Alias versionnés (-001) : on garde le nom stable sans suffixe.
                    if (id.matches(".*-\\d{3}$")) {
                        continue;
                    }
                    if (!supportsGenerateContent(node) || isNonChatGemini(id)) {
                        continue;
                    }
                    boolean vision = AiProviders.supportsVision("gemini", id);
                    out.add(AiModelOption.builder()
                            .id(id)
                            .label(id + (vision ? " (vision)" : ""))
                            .vision(vision)
                            .build());
                }
            }
            pageToken = text(root, "nextPageToken");
        } while (pageToken != null && !pageToken.isBlank());
        return out;
    }

    private List<AiModelOption> fetchOpenAiCompatibleModels(AiProviders.Provider provider, String apiKey)
            throws Exception {
        String url = provider.baseUrl().replaceAll("/+$", "") + "/v1/models";
        RestClient.RequestHeadersSpec<?> req = RestClient.create()
                .get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .accept(MediaType.APPLICATION_JSON);
        if ("openrouter".equals(provider.id())) {
            req = req.header("HTTP-Referer", "https://devicemanager.local")
                    .header("X-Title", "DeviceManager");
        }
        String body = req.retrieve().body(String.class);
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        List<AiModelOption> out = new ArrayList<>();
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            // Certains fournisseurs renvoient { "models": [...] }
            data = root.path("models");
        }
        if (!data.isArray()) {
            return out;
        }
        for (JsonNode node : data) {
            String id = text(node, "id");
            if (id == null) {
                id = text(node, "name");
            }
            if (id == null || !isLikelyChatModel(provider.id(), id, node)) {
                continue;
            }
            String display = text(node, "name");
            if (display == null || display.isBlank() || display.equals(id)) {
                display = id;
            }
            boolean vision = detectVision(provider.id(), id, node);
            out.add(AiModelOption.builder()
                    .id(id)
                    .label(display + (vision ? " (vision)" : ""))
                    .vision(vision)
                    .build());
        }
        return out;
    }

    private static boolean supportsGenerateContent(JsonNode node) {
        JsonNode methods = node.path("supportedGenerationMethods");
        if (!methods.isArray() || methods.isEmpty()) {
            return true;
        }
        for (JsonNode m : methods) {
            if ("generateContent".equalsIgnoreCase(m.asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNonChatGemini(String id) {
        String m = id.toLowerCase(Locale.ROOT);
        return m.contains("embedding")
                || m.contains("aqa")
                || m.contains("imagen")
                || m.contains("tts")
                || m.contains("gecko")
                || m.endsWith("-latest-001") && m.contains("embedding");
    }

    private static boolean isLikelyChatModel(String providerId, String id, JsonNode node) {
        String m = id.toLowerCase(Locale.ROOT);
        if (m.contains("embedding")
                || m.contains("whisper")
                || m.contains("tts")
                || m.contains("dall-e")
                || m.contains("moderation")
                || m.contains("realtime")
                || m.contains("audio")
                || m.contains("transcribe")
                || m.contains("tts-1")) {
            return false;
        }
        // OpenRouter expose architecture.modality
        JsonNode modality = node.path("architecture").path("modality");
        if (modality.isTextual()) {
            String mod = modality.asText("").toLowerCase(Locale.ROOT);
            if (mod.contains("text") || mod.contains("image")) {
                return true;
            }
        }
        if ("openai".equals(providerId)) {
            return m.startsWith("gpt-")
                    || m.startsWith("o1")
                    || m.startsWith("o3")
                    || m.startsWith("o4")
                    || m.startsWith("chatgpt");
        }
        if ("deepseek".equals(providerId)) {
            return m.contains("chat") || m.contains("reasoner");
        }
        return true;
    }

    private static boolean detectVision(String providerId, String id, JsonNode node) {
        JsonNode modality = node.path("architecture").path("modality");
        if (modality.isTextual() && modality.asText("").toLowerCase(Locale.ROOT).contains("image")) {
            return true;
        }
        JsonNode inputModalities = node.path("architecture").path("input_modalities");
        if (inputModalities.isArray()) {
            for (JsonNode mod : inputModalities) {
                if ("image".equalsIgnoreCase(mod.asText())) {
                    return true;
                }
            }
        }
        return AiProviders.supportsVision(providerId, id);
    }

    private static List<AiModelOption> sortModels(List<AiModelOption> models) {
        return models.stream()
                .sorted(Comparator
                        .comparingInt((AiModelOption m) -> modelSortRank(m.getId()))
                        .thenComparing(AiModelOption::isVision).reversed()
                        .thenComparing(m -> m.getId().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** Priorise les modèles Gemini / Flash-Lite courants en tête de liste. */
    private static int modelSortRank(String id) {
        String m = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (m.equals("gemini-3.1-flash-lite") || m.equals("google/gemini-3.1-flash-lite")) {
            return 0;
        }
        if (m.contains("3.1-flash-lite")) {
            return 1;
        }
        if (m.contains("flash-lite") && !m.contains("preview")) {
            return 2;
        }
        if (m.contains("flash") && !m.contains("preview") && !m.contains("image")) {
            return 3;
        }
        if (m.startsWith("gemini-") && !m.contains("preview")) {
            return 4;
        }
        if (m.contains("preview")) {
            return 8;
        }
        return 5;
    }

    private static String friendlyFetchError(String providerId, RestClientResponseException ex) {
        int code = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString() == null ? "" : ex.getResponseBodyAsString();
        if (code == 401 || code == 403
                || body.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED")
                || body.contains("UNAUTHENTICATED")
                || body.contains("invalid authentication")) {
            if ("gemini".equals(providerId)) {
                return "Clé Gemini refusée par Google (souvent les clés « AQ. »). "
                        + "Essayez OpenRouter, ou régénérez une clé AI Studio.";
            }
            return "Authentification refusée par le fournisseur (clé invalide ou expirée).";
        }
        return "Échec HTTP " + code + " lors de la récupération des modèles.";
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record CacheEntry(Instant expiresAt, AiModelsResponse response) {
    }
}

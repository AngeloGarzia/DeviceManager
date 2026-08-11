package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.ai.AiPromptDefaults;
import com.devicemanager.ai.AiProviders;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiLabelScanResponse;
import com.devicemanager.dto.AiProviderAvailability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service de l'assistant IA intégré à DeviceManager.
 * <p>
 * Chat métier, statut des fournisseurs et scan d'étiquettes de pièces détachées
 * casino. La configuration provient des paramètres Setup et des clés API .env ;
 * indépendant du filtre atelier sauf pour le contexte métier des réponses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAssistantService {

    private final AppSettingsService appSettingsService;
    private final AiApiKeyBattery aiApiKeyBattery;
    private final AiModelDiscoveryService aiModelDiscoveryService;
    private final ImageOptimizationService imageOptimizationService;
    private final WebEnrichmentService webEnrichmentService;
    private final ObjectMapper objectMapper;

    /**
     * Indique si l'assistant IA est activé et qu'une clé API est disponible pour le fournisseur courant.
     *
     * @return {@code true} si l'IA peut être invoquée
     */
    public boolean isEnabled() {
        if (!appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)) {
            return false;
        }
        String provider = resolveProviderId();
        return !resolveApiKey(provider).isBlank();
    }

    /**
     * Retourne le statut détaillé de l'assistant et la disponibilité de chaque fournisseur IA.
     *
     * @return message explicatif, drapeau {@code enabled} et liste des fournisseurs
     */
    public AiChatResponse status() {
        String provider = resolveProviderId();
        String model = resolveChatModel(provider);
        String providerLabel = AiProviders.require(provider).label();
        boolean flagOn = appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false);
        String apiKey = resolveApiKey(provider);
        boolean enabled = flagOn && !apiKey.isBlank() && model != null && !model.isBlank();

        String reply;
        if (enabled) {
            reply = "Assistant IA prêt.";
        } else if (!flagOn) {
            reply = "Assistant IA désactivé dans les paramètres — activez « Activer l'assistant IA ».";
        } else if (apiKey.isBlank()) {
            reply = "Fournisseur « " + providerLabel + " » sélectionné mais la clé est manquante. "
                    + "Choisissez un autre fournisseur dans Paramètres ou demandez à un administrateur "
                    + "de configurer la clé.";
        } else {
            reply = "Aucun modèle IA sélectionné — choisissez un modèle dans Paramètres.";
        }

        return AiChatResponse.builder()
                .enabled(enabled)
                .reply(reply)
                .providers(listProviderAvailability())
                .build();
    }

    private List<AiProviderAvailability> listProviderAvailability() {
        return AiProviders.all().stream()
                .map(p -> {
                    String key = aiApiKeyBattery.keyFor(p.id());
                    return AiProviderAvailability.builder()
                            .id(p.id())
                            .label(p.label())
                            .hasApiKey(key != null && !key.isBlank())
                            .build();
                })
                .toList();
    }

    /**
     * Envoie un message utilisateur au modèle de chat configuré.
     *
     * @param message question ou consigne en langage naturel
     * @return réponse textuelle du modèle
     * @throws org.springframework.web.server.ResponseStatusException {@code 503} si IA désactivée ou clé absente ;
     *         {@code 502} en cas d'échec d'appel externe
     */
    public AiChatResponse chat(String message) {
        requireEnabled();
        String provider = resolveProviderId();
        String apiKey = requireApiKey(provider);
        String model = resolveChatModel(provider);

        try {
            ChatClient chatClient = buildChatClient(apiKey, provider, model, 0.3);
            String reply = chatClient.prompt()
                    .system(systemPrompt())
                    .user(message.trim())
                    .call()
                    .content();
            return AiChatResponse.builder()
                    .enabled(true)
                    .reply(reply == null || reply.isBlank() ? "(réponse vide)" : reply.trim())
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec appel Spring AI (model={}): {}", model, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, friendlyAiError(provider, ex));
        }
    }

    /**
     * Analyse une photo d'étiquette : OCR vision + enrichissement web + texte d'usage.
     *
     * @param image fichier image de l'étiquette
     * @return champs extraits (nom, référence, marque, usage, notes)
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si image absente ou vide ;
     *         {@code 502} en cas d'échec d'analyse IA
     */
    public AiLabelScanResponse scanLabel(MultipartFile image) {
        requireEnabled();
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo d'étiquette obligatoire pour le scan");
        }
        VisionEndpoint vision = resolveVisionEndpoint();
        String provider = vision.providerId();
        String apiKey = vision.apiKey();
        String visionModel = vision.model();

        MultipartFile optimized = imageOptimizationService.optimize(image);
        byte[] bytes;
        try {
            bytes = optimized.getBytes();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo illisible ou format non pris en charge");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La photo est vide");
        }

        try {
            ChatClient visionClient = buildChatClient(apiKey, provider, visionModel, 0.1);
            String extractRaw = visionClient.prompt()
                    .user(u -> u.text(labelExtractPrompt())
                            .media(new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(bytes))))
                    .call()
                    .content();

            JsonNode extracted = parseJsonObject(extractRaw);
            String nom = textOrNull(extracted, "nom");
            String reference = textOrNull(extracted, "reference");
            String marque = textOrNull(extracted, "marque");
            String rawText = textOrNull(extracted, "rawText");
            String notes = textOrNull(extracted, "notes");

            String webContext = webEnrichmentService.gatherContext(marque, nom, reference);
            String usage = generateUsage(apiKey, provider, resolveChatModelForProvider(provider),
                    nom, reference, marque, rawText, notes, webContext);

            return AiLabelScanResponse.builder()
                    .enabled(true)
                    .nom(nom)
                    .reference(reference)
                    .marque(marque)
                    .usage(usage)
                    .rawText(rawText)
                    .notes(blankToNull(joinNotes(notes, webContext.isBlank() ? null : "Infos web trouvées pour enrichir l'usage.")))
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec scan étiquette IA: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Analyse de l'étiquette impossible. Réessayez ou changez de fournisseur dans Paramètres.");
        }
    }

    /** Message utilisateur plus clair (notamment clés Gemini AQ. rejetées par Google). */
    private String friendlyAiError(String provider, Exception ex) {
        String chain = exceptionChain(ex);
        boolean geminiAuth = "gemini".equalsIgnoreCase(provider)
                && (chain.contains("401")
                || chain.contains("UNAUTHENTICATED")
                || chain.contains("ACCESS_TOKEN_TYPE_UNSUPPORTED")
                || chain.contains("invalid authentication"));
        if (geminiAuth) {
            return "Clé Gemini refusée. Dans Paramètres, choisissez OpenRouter avec un modèle vision, "
                    + "ou OpenAI.";
        }
        return "Échec de l'appel au modèle IA. Réessayez ou changez de fournisseur dans Paramètres.";
    }

    private static String exceptionChain(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
            }
        }
        return sb.toString();
    }

    private String generateUsage(
            String apiKey,
            String provider,
            String model,
            String nom,
            String reference,
            String marque,
            String rawText,
            String notes,
            String webContext) {
        String prompt = applyUsagePlaceholders(
                usagePromptTemplate(),
                nom,
                reference,
                marque,
                rawText,
                notes,
                webContext);

        ChatClient chatClient = buildChatClient(apiKey, provider, model, 0.4);
        String reply = chatClient.prompt()
                .system(systemPrompt())
                .user(prompt)
                .call()
                .content();
        if (reply == null || reply.isBlank()) {
            return null;
        }
        String cleaned = reply.trim();
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 497) + "...";
        }
        return cleaned;
    }

    private void requireEnabled() {
        if (!appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Assistant IA désactivé dans Paramètres.");
        }
    }

    private String requireApiKey(String provider) {
        String apiKey = resolveApiKey(provider);
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clé IA manquante pour ce fournisseur. Contactez un administrateur ou choisissez un autre fournisseur dans Paramètres.");
        }
        return apiKey;
    }

    /** Clé du fournisseur courant depuis la batterie .env uniquement. */
    private String resolveApiKey(String provider) {
        return aiApiKeyBattery.keyFor(provider);
    }

    private String resolveProviderId() {
        return AiProviders.normalizeProvider(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai"));
    }

    private String systemPrompt() {
        return firstNonBlank(
                appSettingsService.get(AppSettingsService.AI_SYSTEM_PROMPT, ""),
                AiPromptDefaults.SYSTEM);
    }

    private String labelExtractPrompt() {
        return firstNonBlank(
                appSettingsService.get(AppSettingsService.AI_LABEL_EXTRACT_PROMPT, ""),
                AiPromptDefaults.LABEL_EXTRACT);
    }

    private String usagePromptTemplate() {
        return firstNonBlank(
                appSettingsService.get(AppSettingsService.AI_USAGE_PROMPT, ""),
                AiPromptDefaults.USAGE);
    }

    private static String applyUsagePlaceholders(
            String template,
            String nom,
            String reference,
            String marque,
            String rawText,
            String notes,
            String webContext) {
        String web = webContext == null || webContext.isBlank() ? "(aucun résultat web)" : webContext;
        return template
                .replace("{{nom}}", nullToDash(nom))
                .replace("{{reference}}", nullToDash(reference))
                .replace("{{marque}}", nullToDash(marque))
                .replace("{{rawText}}", nullToDash(rawText))
                .replace("{{notes}}", nullToDash(notes))
                .replace("{{webContext}}", web);
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return fallback;
    }

    private String resolveChatModel(String provider) {
        return resolveChatModelForProvider(provider);
    }

    private String resolveChatModelForProvider(String provider) {
        // Pour le fournisseur courant, respecter le modèle Setup ; sinon premier modèle découvert en ligne.
        if (provider.equals(resolveProviderId())) {
            String model = appSettingsService.get(AppSettingsService.AI_MODEL, "");
            if (model != null && !model.isBlank()) {
                return model.trim();
            }
        }
        String online = aiModelDiscoveryService.firstModelId(provider);
        return online == null ? "" : online;
    }

    private record VisionEndpoint(String providerId, String model, String apiKey) {
    }

    /**
     * Endpoint vision : fournisseur courant s'il a un modèle vision + clé,
     * sinon premier autre fournisseur avec clé et modèle vision (ex. Gemini).
     */
    private VisionEndpoint resolveVisionEndpoint() {
        String preferred = resolveProviderId();
        VisionEndpoint local = tryVisionEndpoint(preferred);
        if (local != null) {
            return local;
        }
        for (String id : AiProviders.providerIds()) {
            if (id.equals(preferred)) {
                continue;
            }
            VisionEndpoint alt = tryVisionEndpoint(id);
            if (alt != null) {
                log.info("Scan étiquette : {} sans vision accessible → fallback {}", preferred, id);
                return alt;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Aucun fournisseur capable de lire les photos d'étiquettes. "
                        + "Dans Paramètres, choisissez Gemini ou OpenRouter avec un modèle vision.");
    }

    private VisionEndpoint tryVisionEndpoint(String providerId) {
        String key = resolveApiKey(providerId);
        if (key.isBlank()) {
            return null;
        }
        String configured = resolveChatModelForProvider(providerId);
        String visionModel;
        if (configured != null && !configured.isBlank() && AiProviders.supportsVision(providerId, configured)) {
            visionModel = configured;
        } else {
            visionModel = aiModelDiscoveryService.firstVisionModelId(providerId);
        }
        if (visionModel == null || visionModel.isBlank()) {
            return null;
        }
        return new VisionEndpoint(providerId, visionModel, key);
    }

    private ChatClient buildChatClient(String apiKey, String providerId, String model, double temperature) {
        // Les clés Gemini récentes (préfixe AQ.) refusent l'endpoint OpenAI-compatible + Bearer.
        // On passe par l'API native (x-goog-api-key) via le client Google GenAI.
        if ("gemini".equalsIgnoreCase(providerId)) {
            Client genAiClient = Client.builder()
                    .apiKey(apiKey)
                    .build();
            GoogleGenAiChatModel geminiModel = GoogleGenAiChatModel.builder()
                    .genAiClient(genAiClient)
                    .defaultOptions(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .build())
                    .build();
            return ChatClient.builder(geminiModel).build();
        }

        AiProviders.Provider provider = AiProviders.require(providerId);
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(provider.baseUrl())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private JsonNode parseJsonObject(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "L'assistant n'a pas pu générer de réponse. Réessayez.");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return objectMapper.readTree(cleaned);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText(null);
        return blankToNull(v);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private static String joinNotes(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + " | " + b;
    }
}

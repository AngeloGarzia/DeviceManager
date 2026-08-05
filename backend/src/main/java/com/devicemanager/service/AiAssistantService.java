package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.ai.AiProviders;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiLabelScanResponse;
import com.devicemanager.dto.AiProviderAvailability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
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

    public static final String SYSTEM_PROMPT = """
            Tu es l'assistant DeviceManager, une application de gestion de pièces détachées
            pour machines à sous (casinos / ateliers techniques).

            Tu aides les administrateurs et techniciens à :
            - comprendre le catalogue pièces, MAS, SFM et demandes de commande ;
            - rédiger un message de demande de commande clair ;
            - expliquer les statuts (PENDING / VALIDATED) et le flux de validation ;
            - proposer des bonnes pratiques (références, photos, contacts SFM).

            Réponds en français, de façon concise et professionnelle.
            Si tu manques d'information métier précise (stock réel, IDs), dis-le clairement
            plutôt que d'inventer.
            """;

    private static final String LABEL_EXTRACT_PROMPT = """
            Tu analyses une photo d'étiquette / plaque signalétique d'une pièce détachée
            (électronique, mécanique, casino / machines à sous).

            Extrais les informations visibles et réponds UNIQUEMENT avec un JSON valide, sans markdown :
            {
              "nom": "nom commercial du produit si lisible, sinon null",
              "reference": "référence / part number / P/N si lisible, sinon null",
              "marque": "marque / fabricant si lisible, sinon null",
              "rawText": "texte utile lu sur l'étiquette (court)",
              "notes": "autres infos utiles (lot, voltage, etc.) ou null"
            }
            Ne invente pas de valeurs absentes de l'image : utilise null.
            """;

    private final AppSettingsService appSettingsService;
    private final AiApiKeyBattery aiApiKeyBattery;
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
        String model = appSettingsService.get(AppSettingsService.AI_MODEL, AiProviders.defaultModel(provider));
        String providerLabel = AiProviders.require(provider).label();
        boolean flagOn = appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false);
        String apiKey = resolveApiKey(provider);
        boolean enabled = flagOn && !apiKey.isBlank();

        String reply;
        if (enabled) {
            reply = "Assistant IA prêt (" + providerLabel + " / " + model + ").";
        } else if (!flagOn) {
            reply = "Assistant IA désactivé dans les paramètres — activez « Activer l'assistant IA ».";
        } else {
            String envVar = AiApiKeyBattery.envVarName(provider);
            reply = "Fournisseur « " + providerLabel + " » sélectionné mais " + envVar
                    + " est vide. Choisissez un fournisseur avec clé (ex. Groq) "
                    + "ou renseignez " + envVar + " dans le .env / Render.";
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
                    .system(SYSTEM_PROMPT)
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Échec de l'appel au modèle IA: " + ex.getMessage());
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image obligatoire");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image illisible");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image vide");
        }

        try {
            ChatClient visionClient = buildChatClient(apiKey, provider, visionModel, 0.1);
            String extractRaw = visionClient.prompt()
                    .user(u -> u.text(LABEL_EXTRACT_PROMPT)
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
                    "Échec de l'analyse IA de l'étiquette: " + ex.getMessage());
        }
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
        String prompt = """
                Rédige un texte COURT (2 à 4 phrases max, français) pour le champ « usage »
                d'une fiche pièce détachée casino / machines à sous.

                Données lues sur l'étiquette :
                - nom: %s
                - référence: %s
                - marque: %s
                - texte brut: %s
                - notes: %s

                Contexte web (peut être vide ou partiel) :
                %s

                Consigne : décrire à quoi sert typiquement la pièce, son contexte d'utilisation,
                sans inventer de références absentes. Si peu d'infos, rester prudent et générique.
                Réponds uniquement avec le texte d'usage, sans titre ni JSON.
                """.formatted(
                nullToDash(nom),
                nullToDash(reference),
                nullToDash(marque),
                nullToDash(rawText),
                nullToDash(notes),
                webContext == null || webContext.isBlank() ? "(aucun résultat web)" : webContext
        );

        ChatClient chatClient = buildChatClient(apiKey, provider, model, 0.4);
        String reply = chatClient.prompt()
                .system(SYSTEM_PROMPT)
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
                    "Assistant IA désactivé dans Setup");
        }
    }

    private String requireApiKey(String provider) {
        String apiKey = resolveApiKey(provider);
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clé API IA manquante pour le fournisseur « " + provider
                            + " » (variable .env de la batterie IA)");
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

    private String resolveChatModel(String provider) {
        return resolveChatModelForProvider(provider);
    }

    private String resolveChatModelForProvider(String provider) {
        // Pour le fournisseur courant, respecter le modèle Setup ; sinon modèle par défaut du fournisseur.
        if (provider.equals(resolveProviderId())) {
            String model = appSettingsService.get(AppSettingsService.AI_MODEL, AiProviders.defaultModel(provider));
            if (model != null && !model.isBlank()) {
                return model.trim();
            }
        }
        return AiProviders.defaultModel(provider);
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
                "Aucun fournisseur vision disponible. "
                        + "Groq n’offre souvent pas de modèle vision : choisissez Gemini ou OpenRouter "
                        + "dans Paramètres, ou renseignez GEMINI_API_KEY / OPENROUTER_API_KEY.");
    }

    private VisionEndpoint tryVisionEndpoint(String providerId) {
        String key = resolveApiKey(providerId);
        if (key.isBlank()) {
            return null;
        }
        String configured = resolveChatModelForProvider(providerId);
        String visionModel;
        if (AiProviders.supportsVision(providerId, configured)) {
            visionModel = configured;
        } else {
            visionModel = AiProviders.visionFallbackModel(providerId);
        }
        if (visionModel == null || visionModel.isBlank()) {
            return null;
        }
        return new VisionEndpoint(providerId, visionModel, key);
    }

    private ChatClient buildChatClient(String apiKey, String providerId, String model, double temperature) {
        AiProviders.Provider provider = AiProviders.require(providerId);
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(provider.baseUrl());
        OpenAiApi openAiApi = apiBuilder.build();
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse IA vide");
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

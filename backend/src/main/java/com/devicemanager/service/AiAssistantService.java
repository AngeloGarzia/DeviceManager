package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.ai.AiProviders;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiLabelScanResponse;
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

    public boolean isEnabled() {
        if (!appSettingsService.getBoolean(AppSettingsService.AI_ENABLED, false)) {
            return false;
        }
        String provider = resolveProviderId();
        return !resolveApiKey(provider).isBlank();
    }

    public AiChatResponse status() {
        boolean enabled = isEnabled();
        String provider = resolveProviderId();
        String model = appSettingsService.get(AppSettingsService.AI_MODEL, AiProviders.defaultModel(provider));
        String providerLabel = AiProviders.require(provider).label();
        return AiChatResponse.builder()
                .enabled(enabled)
                .reply(enabled
                        ? "Assistant IA prêt (" + providerLabel + " / " + model + ")."
                        : "Assistant IA désactivé. Activez-le dans Setup et renseignez la clé "
                                + "du fournisseur (batterie .env ou Setup).")
                .build();
    }

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
     */
    public AiLabelScanResponse scanLabel(MultipartFile image) {
        requireEnabled();
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image obligatoire");
        }
        String provider = resolveProviderId();
        String apiKey = requireApiKey(provider);
        String visionModel = resolveVisionModel(provider);

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
            String usage = generateUsage(apiKey, provider, resolveChatModel(provider),
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
                            + " » (.env batterie ou Setup AI_API_KEY)");
        }
        return apiKey;
    }

    /** Batterie .env du fournisseur, sinon clé Setup AI_API_KEY. */
    private String resolveApiKey(String provider) {
        String fromEnv = aiApiKeyBattery.keyFor(provider);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromSetup = appSettingsService.get(AppSettingsService.AI_API_KEY, "");
        return fromSetup == null ? "" : fromSetup.trim();
    }

    private String resolveProviderId() {
        return AiProviders.normalizeProvider(appSettingsService.get(AppSettingsService.AI_PROVIDER, "openai"));
    }

    private String resolveChatModel(String provider) {
        String model = appSettingsService.get(AppSettingsService.AI_MODEL, AiProviders.defaultModel(provider));
        if (model == null || model.isBlank()) {
            return AiProviders.defaultModel(provider);
        }
        return model.trim();
    }

    /** Vision : modèle multimodal du fournisseur, sinon erreur claire. */
    private String resolveVisionModel(String provider) {
        String model = resolveChatModel(provider);
        if (AiProviders.supportsVision(provider, model)) {
            return model;
        }
        String fallback = AiProviders.visionFallbackModel(provider);
        if (fallback != null) {
            log.info("Modèle {} sans vision → fallback vision {}", model, fallback);
            return fallback;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le fournisseur/modèle sélectionné ne gère pas la vision. "
                        + "Choisissez un modèle vision (ex. gpt-4o-mini, pixtral, ou OpenRouter/Claude/Gemini).");
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

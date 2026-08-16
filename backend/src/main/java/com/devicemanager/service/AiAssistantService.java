package com.devicemanager.service;

import com.devicemanager.ai.AiApiKeyBattery;
import com.devicemanager.ai.AiPromptDefaults;
import com.devicemanager.ai.AiProviders;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiDevisOrderLineContext;
import com.devicemanager.dto.AiDevisScanResponse;
import com.devicemanager.dto.AiDevisSuggestion;
import com.devicemanager.dto.AiDevisUnmatchedPart;
import com.devicemanager.dto.AiLabelScanResponse;
import com.devicemanager.dto.AiPdfScanResponse;
import com.devicemanager.dto.AiProviderAvailability;
import com.devicemanager.security.DeviceDocumentTypes;
import com.devicemanager.security.FileMagicBytesValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.genai.Client;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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

    /**
     * Analyse un PDF (manuel / datasheet / notice) et propose une fiche « information technique ».
     *
     * @param pdf     fichier PDF
     * @param docType {@code MANUAL}, {@code DATASHEET} ou {@code NOTICE} (optionnel)
     * @param nom     nom de pièce déjà saisi (contexte, optionnel)
     * @param reference référence déjà saisie (contexte, optionnel)
     */
    public AiPdfScanResponse analyzePdf(
            MultipartFile pdf,
            String docType,
            String nom,
            String reference) {
        requireEnabled();
        if (pdf == null || pdf.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier PDF obligatoire");
        }
        String type = DeviceDocumentTypes.normalize(docType);
        if (type == null) {
            type = DeviceDocumentTypes.DATASHEET;
        }
        byte[] bytes;
        try {
            bytes = pdf.getBytes();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF illisible");
        }
        FileMagicBytesValidator.validatePdfMagicBytes(bytes);

        String extractedText = extractPdfText(bytes);
        if (extractedText == null || extractedText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun texte extractible dans ce PDF (scan image ou PDF protégé). "
                            + "Essayez un PDF textuel ou saisissez les informations manuellement.");
        }
        if (extractedText.length() > 14_000) {
            extractedText = extractedText.substring(0, 14_000) + "\n…";
        }

        String provider = resolveProviderId();
        String apiKey = requireApiKey(provider);
        String model = resolveChatModelForProvider(provider);

        String typeLabel = switch (type) {
            case DeviceDocumentTypes.MANUAL -> "manuel";
            case DeviceDocumentTypes.NOTICE -> "notice";
            default -> "datasheet / fiche technique";
        };

        String prompt = """
                Tu es un technicien casino. Analyse le texte extrait d'un %s de pièce détachée.
                Produis UNIQUEMENT un JSON :
                {"informationTechnique":"...","notes":"..."}
                - informationTechnique : synthèse claire et structurée (caractéristiques, références,
                  compatibilité, précautions, procédure résumée si présente). Max 4000 caractères.
                  En français. Pas de markdown.
                - notes : incertitudes ou absences d'info (peut être null).
                Contexte pièce (peut être vide) : nom=%s ; référence=%s
                Texte PDF :
                ---
                %s
                ---
                """.formatted(
                typeLabel,
                nullToDash(blankToNull(nom)),
                nullToDash(blankToNull(reference)),
                extractedText);

        try {
            ChatClient chatClient = buildChatClient(apiKey, provider, model, 0.2);
            String reply = chatClient.prompt()
                    .system(systemPrompt())
                    .user(prompt)
                    .call()
                    .content();
            JsonNode node = parseJsonObject(reply);
            String info = textOrNull(node, "informationTechnique");
            if (info == null || info.isBlank()) {
                // Fallback : utiliser le corps de réponse nettoyé
                info = blankToNull(reply);
            }
            if (info != null && info.length() > 8000) {
                info = info.substring(0, 7997) + "...";
            }
            if (info == null || info.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "L'IA n'a pas pu extraire d'informations techniques de ce PDF.");
            }
            return AiPdfScanResponse.builder()
                    .enabled(true)
                    .informationTechnique(info)
                    .notes(textOrNull(node, "notes"))
                    .build();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec analyse PDF IA: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    friendlyAiError(provider, ex));
        }
    }

    /**
     * Analyse un devis PDF et propose, pour chaque pièce de la commande, une désignation /
     * référence à aligner (si trouvée dans le devis).
     *
     * @param pdfBytes contenu PDF
     * @param lines    pièces de la commande (deviceId, nom, référence actuels)
     */
    public AiDevisScanResponse analyzeDevis(byte[] pdfBytes, List<AiDevisOrderLineContext> lines) {
        requireEnabled();
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier PDF obligatoire");
        }
        FileMagicBytesValidator.validatePdfMagicBytes(pdfBytes);

        List<AiDevisOrderLineContext> safeLines = lines == null ? List.of() : lines.stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getDeviceId() != null)
                .toList();
        if (safeLines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La commande ne contient aucune pièce à rapprocher du devis.");
        }

        String extractedText = extractPdfText(pdfBytes);
        if (extractedText == null || extractedText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun texte extractible dans ce devis PDF (scan image ou PDF protégé).");
        }
        if (extractedText.length() > 16_000) {
            extractedText = extractedText.substring(0, 16_000) + "\n…";
        }

        StringBuilder linesBlock = new StringBuilder();
        for (AiDevisOrderLineContext line : safeLines) {
            linesBlock.append("- deviceId=").append(line.getDeviceId())
                    .append(" ; nom=").append(nullToDash(blankToNull(line.getNom())))
                    .append(" ; reference=").append(nullToDash(blankToNull(line.getReference())))
                    .append('\n');
        }

        String provider = resolveProviderId();
        String apiKey = requireApiKey(provider);
        String model = resolveChatModelForProvider(provider);

        String prompt = """
                Tu es un technicien casino. Analyse le texte d'un DEVIS fournisseur (PDF).
                Repère les lignes de pièces détachées (désignation / désignation commerciale, référence / code article).
                Relie chaque pièce de la COMMANDE ci-dessous à la meilleure ligne du devis.
                Produis UNIQUEMENT un JSON :
                {
                  "matches":[
                    {
                      "deviceId":123,
                      "suggestedNom":"...",
                      "suggestedReference":"...",
                      "confidence":"HIGH|MEDIUM|LOW"
                    }
                  ],
                  "unmatched":[{"designation":"...","reference":"..."}],
                  "notes":"..."
                }
                Règles :
                - matches : une entrée par deviceId de la commande UNIQUEMENT si une correspondance plausible existe.
                - suggestedNom = désignation du devis (nettoyer espaces) ; null si identique / absente.
                - suggestedReference = référence article du devis ; null si identique / absente.
                - confidence HIGH si référence exacte ou quasi exacte ; MEDIUM si nom proche ; LOW sinon.
                - unmatched : lignes devis sans pièce commande correspondante (peut être []).
                - notes : incertitudes (peut être null). Pas de markdown.
                Pièces de la commande :
                %s
                Texte devis :
                ---
                %s
                ---
                """.formatted(linesBlock, extractedText);

        try {
            ChatClient chatClient = buildChatClient(apiKey, provider, model, 0.1);
            String reply = chatClient.prompt()
                    .system(systemPrompt())
                    .user(prompt)
                    .call()
                    .content();
            JsonNode node = parseJsonObject(reply);
            return buildDevisScanResponse(safeLines, node);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Échec analyse devis IA: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    friendlyAiError(provider, ex));
        }
    }

    private AiDevisScanResponse buildDevisScanResponse(
            List<AiDevisOrderLineContext> lines,
            JsonNode node) {
        java.util.Map<Long, AiDevisOrderLineContext> byId = new java.util.LinkedHashMap<>();
        for (AiDevisOrderLineContext line : lines) {
            byId.put(line.getDeviceId(), line);
        }

        List<AiDevisSuggestion> suggestions = new ArrayList<>();
        JsonNode matches = node.get("matches");
        if (matches != null && matches.isArray()) {
            for (JsonNode m : matches) {
                Long deviceId = longOrNull(m, "deviceId");
                if (deviceId == null || !byId.containsKey(deviceId)) {
                    continue;
                }
                AiDevisOrderLineContext current = byId.get(deviceId);
                String suggestedNom = textOrNull(m, "suggestedNom");
                String suggestedReference = textOrNull(m, "suggestedReference");
                String confidence = textOrNull(m, "confidence");
                if (confidence == null) {
                    confidence = "MEDIUM";
                } else {
                    confidence = confidence.trim().toUpperCase(Locale.ROOT);
                    if (!List.of("HIGH", "MEDIUM", "LOW").contains(confidence)) {
                        confidence = "MEDIUM";
                    }
                }
                boolean nomChanged = differsIgnoringCase(current.getNom(), suggestedNom);
                boolean refChanged = differsIgnoringCase(current.getReference(), suggestedReference);
                if (!nomChanged && !refChanged) {
                    continue;
                }
                suggestions.add(AiDevisSuggestion.builder()
                        .deviceId(deviceId)
                        .currentNom(current.getNom())
                        .currentReference(current.getReference())
                        .suggestedNom(nomChanged ? suggestedNom : null)
                        .suggestedReference(refChanged ? suggestedReference : null)
                        .confidence(confidence)
                        .hasChanges(true)
                        .build());
            }
        }

        List<AiDevisUnmatchedPart> unmatched = new ArrayList<>();
        JsonNode unmatchedNode = node.get("unmatched");
        if (unmatchedNode != null && unmatchedNode.isArray()) {
            for (JsonNode u : unmatchedNode) {
                String designation = textOrNull(u, "designation");
                String reference = textOrNull(u, "reference");
                if (designation == null && reference == null) {
                    continue;
                }
                unmatched.add(AiDevisUnmatchedPart.builder()
                        .designation(designation)
                        .reference(reference)
                        .build());
            }
        }

        return AiDevisScanResponse.builder()
                .enabled(true)
                .notes(textOrNull(node, "notes"))
                .suggestions(suggestions)
                .unmatched(unmatched)
                .build();
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v.isNumber()) {
            return v.longValue();
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean differsIgnoringCase(String current, String suggested) {
        if (suggested == null || suggested.isBlank()) {
            return false;
        }
        if (current == null || current.isBlank()) {
            return true;
        }
        return !current.trim().equalsIgnoreCase(suggested.trim());
    }

    private static String extractPdfText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de lire le PDF : " + ex.getMessage());
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
                    "Clé IA manquante pour ce fournisseur. "
                    + "Contactez un administrateur ou choisissez un autre fournisseur dans Paramètres.");
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

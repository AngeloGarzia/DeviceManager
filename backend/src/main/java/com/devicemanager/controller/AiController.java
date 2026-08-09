package com.devicemanager.controller;

import com.devicemanager.dto.AiChatRequest;
import com.devicemanager.dto.AiChatResponse;
import com.devicemanager.dto.AiLabelScanResponse;
import com.devicemanager.dto.AiModelsResponse;
import com.devicemanager.service.AiAssistantService;
import com.devicemanager.service.AiModelDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôleur REST de l'assistant IA pour DeviceManager.
 * <p>
 * Expose le statut, le chat métier et le scan d'étiquettes de pièces détachées.
 * Réservé aux administrateurs et techniciens ; les opérations respectent le contexte
 * atelier courant ({@code X-Atelier-Id}) pour les données métier consultées.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final AiModelDiscoveryService aiModelDiscoveryService;

    /**
     * Retourne la disponibilité de l'assistant IA et la liste des fournisseurs configurés.
     *
     * @return statut d'activation, message explicatif et fournisseurs avec clé API
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiChatResponse> status() {
        return ResponseEntity.ok(aiAssistantService.status());
    }

    /**
     * Liste les modèles chat disponibles en ligne chez le fournisseur (pas de catalogue en dur).
     *
     * @param provider identifiant fournisseur (ex. {@code gemini}, {@code openrouter})
     */
    @GetMapping("/models")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiModelsResponse> models(
            @RequestParam(value = "provider", required = false) String provider) {
        return ResponseEntity.ok(aiModelDiscoveryService.listModels(provider));
    }

    /**
     * Envoie un message à l'assistant IA (catalogue pièces, MAS, SFM, demandes de commande).
     *
     * @param request message utilisateur
     * @return réponse textuelle du modèle
     * @throws org.springframework.web.server.ResponseStatusException {@code 503} si l'IA est désactivée ;
     *         {@code 502} en cas d'échec d'appel au fournisseur
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiAssistantService.chat(request.getMessage()));
    }

    /**
     * Analyse une photo d'étiquette de pièce détachée (OCR vision + enrichissement web).
     *
     * @param image fichier image multipart
     * @return champs extraits (nom, référence, marque, usage, etc.)
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si l'image est absente ou illisible ;
     *         {@code 502} en cas d'échec d'analyse IA
     */
    @PostMapping(value = "/label-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<AiLabelScanResponse> labelScan(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(aiAssistantService.scanLabel(image));
    }
}

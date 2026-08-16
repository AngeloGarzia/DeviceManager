package com.devicemanager.controller;

import com.devicemanager.dto.AiDevisApplyRequest;
import com.devicemanager.dto.AiDevisApplyResponse;
import com.devicemanager.dto.AiDevisScanResponse;
import com.devicemanager.dto.MailPreviewItem;
import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.service.OrderRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST des demandes de commande de pièces détachées.
 * <p>
 * Flux : création → validation admin + e-mails SFM → ajustement quantités →
 * réception ({@code RECEIVED}) avec mise à jour du stock. Scopé à l'atelier
 * courant ({@code X-Atelier-Id}).
 */
@RestController
@RequestMapping("/api/order-requests")
@RequiredArgsConstructor
public class OrderRequestController {

    private final OrderRequestService orderRequestService;

    /**
     * Crée une demande de commande — admin et technicien.
     *
     * @param request lignes de pièces et message du demandeur
     * @param authentication utilisateur authentifié
     * @return demande enregistrée (statut {@code PENDING})
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si aucune ligne ou pièce introuvable
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<OrderRequestResponse> create(
            @Valid @RequestBody OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderRequestService.create(request, authentication.getName()));
    }

    /**
     * Liste les demandes de commande de l'atelier courant — admin et technicien.
     *
     * @param masIds si renseigné, ne garde que les commandes dont une pièce est liée à ces MAS
     * @return demandes triées par date décroissante
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<OrderRequestResponse>> list(
            @RequestParam(required = false) List<Long> masIds) {
        if (masIds != null && !masIds.isEmpty()) {
            return ResponseEntity.ok(orderRequestService.findByMasIds(masIds));
        }
        return ResponseEntity.ok(orderRequestService.findAll());
    }

    /**
     * Compte les demandes en attente de validation dans l'atelier courant.
     *
     * @return mappe {@code count} vers le nombre de demandes {@code PENDING} ou {@code SENT}
     */
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return ResponseEntity.ok(Map.of("count", orderRequestService.countPending()));
    }

    /**
     * Aperçu des e-mails (admin + SFM) qui seraient envoyés à la création, sans persister.
     *
     * @param request brouillon de demande
     * @param authentication utilisateur authentifié
     * @return aperçus des messages
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si lignes invalides
     */
    @PostMapping("/mail-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<MailPreviewItem>> previewCreate(
            @RequestBody OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.previewCreateMails(request, authentication.getName()));
    }

    /**
     * Aperçu des e-mails SFM (consultation) — admin et technicien.
     *
     * @param id identifiant de la demande
     * @param authentication utilisateur connecté (signature dans l'aperçu si admin)
     * @return aperçus des e-mails fournisseurs
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si demande introuvable
     */
    @GetMapping("/{id}/mail-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<MailPreviewItem>> previewValidate(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.previewSfmMails(id, authentication.getName()));
    }

    /**
     * Validation / envoi SFM — admin uniquement.
     *
     * @param id identifiant de la demande
     * @param authentication administrateur validant la demande
     * @return demande passée en statut {@code VALIDATED}
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} si déjà validée
     */
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderRequestResponse> validate(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.validate(id, authentication.getName()));
    }

    /**
     * Ajuste les lignes / quantités d'une demande non réceptionnée — admin uniquement.
     *
     * @param id identifiant de la demande
     * @param request nouvelles lignes et message
     * @param authentication administrateur
     * @return demande mise à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderRequestResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.update(id, request, authentication.getName()));
    }

    /**
     * Confirme la réception : statut {@code RECEIVED} + incrément du stock des pièces.
     * Le corps peut contenir les quantités réellement reçues (ajustement avant réception).
     *
     * @param id identifiant de la demande
     * @param request lignes finales optionnelles
     * @param authentication administrateur
     * @return demande réceptionnée
     */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderRequestResponse> receive(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.receive(id, request, authentication.getName()));
    }

    /**
     * Associe un devis (PDF ou image) à une commande validée (ou réceptionnée) — admin uniquement.
     *
     * @param id             identifiant de la demande
     * @param file           fichier PDF ou capture ({@code multipart} part {@code file})
     * @param authentication administrateur
     * @return demande avec métadonnées du devis
     */
    @PostMapping(value = "/{id}/devis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderRequestResponse> attachDevis(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.attachDevis(id, file, authentication.getName()));
    }

    /**
     * Analyse un devis PDF (IA) et propose des mises à jour nom/référence pour les pièces.
     */
    @PostMapping(value = "/{id}/devis/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiDevisScanResponse> analyzeDevis(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(orderRequestService.analyzeDevis(id, file));
    }

    /**
     * Applique les mises à jour acceptées après analyse du devis.
     */
    @PostMapping("/{id}/devis/apply-updates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiDevisApplyResponse> applyDevisUpdates(
            @PathVariable Long id,
            @Valid @RequestBody AiDevisApplyRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                orderRequestService.applyDevisUpdates(id, request, authentication.getName()));
    }

    /**
     * Suppression — admin uniquement (interdit si déjà réceptionnée).
     *
     * @param id identifiant de la demande
     * @param authentication administrateur effectuant la suppression
     * @return réponse vide ({@code 204})
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        orderRequestService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

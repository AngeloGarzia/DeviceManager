package com.devicemanager.controller;

import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.dto.DeviceStockUpdateRequest;
import com.devicemanager.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Contrôleur REST du catalogue de pièces détachées (devices).
 * <p>
 * CRUD des fiches pièces avec photos, liens MAS/SFM et recherche textuelle.
 * Toutes les opérations sont scopées à l'atelier courant ({@code X-Atelier-Id}).
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Liste les pièces détachées de l'atelier courant, avec recherche optionnelle.
     *
     * @param q termes de recherche (nom, référence, usage, SFM, MAS, marque)
     * @return pièces de l'atelier actif
     */
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(deviceService.findAll(q));
    }

    /**
     * Retourne une pièce détachée par identifiant dans l'atelier courant.
     *
     * @param id identifiant de la pièce
     * @return fiche complète avec photos
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable dans l'atelier
     */
    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }

    /**
     * Crée une pièce détachée avec photos dans l'atelier courant.
     *
     * @param data métadonnées de la pièce (JSON multipart)
     * @param photos images associées (au moins une requise)
     * @return pièce créée
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si validation ou photos invalides ;
     *         {@code 409} en cas de doublon nom/référence
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestPart("data") DeviceRequest data,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {
        List<MultipartFile> list = photos == null ? List.of() : Arrays.asList(photos);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.create(data, list));
    }

    /**
     * Met à jour uniquement la quantité en stock d'une pièce détachée.
     *
     * @param id identifiant de la pièce
     * @param body quantité en stock (≥ 0)
     * @return pièce mise à jour
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @PatchMapping("/{id}/stock")
    public ResponseEntity<DeviceResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody DeviceStockUpdateRequest body) {
        return ResponseEntity.ok(deviceService.updateStock(id, body.getStock()));
    }

    /**
     * Met à jour une pièce détachée et remplace éventuellement ses photos.
     *
     * @param id identifiant de la pièce
     * @param data métadonnées mises à jour
     * @param photos nouvelles images à ajouter
     * @return pièce modifiée
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} en cas de conflit nom/référence
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceResponse> update(
            @PathVariable Long id,
            @Valid @RequestPart("data") DeviceRequest data,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {
        List<MultipartFile> list = photos == null ? List.of() : Arrays.asList(photos);
        return ResponseEntity.ok(deviceService.update(id, data, list));
    }

    /**
     * Supprime une pièce détachée et ses fichiers stockés.
     *
     * @param id identifiant de la pièce
     * @return réponse vide ({@code 204})
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

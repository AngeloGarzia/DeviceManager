package com.devicemanager.controller;

import com.devicemanager.dto.SfmRequest;
import com.devicemanager.dto.SfmResponse;
import com.devicemanager.service.SfmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST des SFM (fournisseurs de pièces détachées).
 * <p>
 * CRUD des fournisseurs, contacts et marques couvertes, scopé à l'atelier
 * courant via l'en-tête {@code X-Atelier-Id}.
 */
@RestController
@RequestMapping("/api/sfm")
@RequiredArgsConstructor
public class SfmController {

    private final SfmService sfmService;

    /**
     * Liste les SFM de l'atelier courant, avec recherche optionnelle.
     *
     * @param q filtre textuel sur le nom ou les contacts
     * @return fournisseurs de l'atelier actif
     */
    @GetMapping
    public ResponseEntity<List<SfmResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(sfmService.findAll(q));
    }

    /**
     * Retourne un SFM par identifiant dans l'atelier courant.
     *
     * @param id identifiant du SFM
     * @return fiche fournisseur avec contacts et marques
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @GetMapping("/{id}")
    public ResponseEntity<SfmResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(sfmService.findById(id));
    }

    /**
     * Crée un SFM dans l'atelier courant.
     *
     * @param request nom, contacts et marques couvertes
     * @return SFM créé
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si contacts ou marques manquants ;
     *         {@code 409} en cas de conflit de nom
     */
    @PostMapping
    public ResponseEntity<SfmResponse> create(@Valid @RequestBody SfmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sfmService.create(request));
    }

    /**
     * Met à jour un SFM de l'atelier courant.
     *
     * @param id identifiant du SFM
     * @param request données mises à jour
     * @return SFM modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} en cas de conflit de nom
     */
    @PutMapping("/{id}")
    public ResponseEntity<SfmResponse> update(@PathVariable Long id, @Valid @RequestBody SfmRequest request) {
        return ResponseEntity.ok(sfmService.update(id, request));
    }

    /**
     * Supprime un SFM de l'atelier courant.
     *
     * @param id identifiant du SFM
     * @return réponse vide ({@code 204})
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sfmService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

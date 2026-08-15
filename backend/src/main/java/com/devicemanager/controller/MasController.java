package com.devicemanager.controller;

import com.devicemanager.dto.DenoRequest;
import com.devicemanager.dto.DenoResponse;
import com.devicemanager.dto.MarqueMasRequest;
import com.devicemanager.dto.MarqueMasResponse;
import com.devicemanager.dto.MasRequest;
import com.devicemanager.dto.MasResponse;
import com.devicemanager.service.MasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST des MAS (Machines À Sous) et marques associées.
 * <p>
 * Gère le référentiel MAS par atelier casino ; les opérations MAS sont filtrées
 * par l'atelier courant ({@code X-Atelier-Id}). Les marques sont partagées globalement.
 */
@RestController
@RequestMapping("/api/mas")
@RequiredArgsConstructor
public class MasController {

    private final MasService masService;

    /**
     * Liste les MAS de l'atelier courant, avec recherche optionnelle.
     *
     * @param q filtre textuel sur le numéro ou la marque
     * @return MAS de l'atelier actif
     */
    @GetMapping
    public ResponseEntity<List<MasResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masService.findAll(q));
    }

    /**
     * Liste toutes les marques MAS disponibles (référentiel global).
     *
     * @return marques triées par libellé
     */
    @GetMapping("/marques")
    public ResponseEntity<List<MarqueMasResponse>> marques() {
        return ResponseEntity.ok(masService.listMarques());
    }

    /**
     * Crée une nouvelle marque MAS.
     *
     * @param request libellé de la marque
     * @return marque créée avec code généré
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si le libellé existe déjà
     */
    @PostMapping("/marques")
    public ResponseEntity<MarqueMasResponse> createMarque(@Valid @RequestBody MarqueMasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masService.createMarque(request));
    }

    /**
     * Liste les dénominations MAS (référentiel global).
     */
    @GetMapping("/denos")
    public ResponseEntity<List<DenoResponse>> denos() {
        return ResponseEntity.ok(masService.listDenos());
    }

    /**
     * Crée une dénomination MAS.
     */
    @PostMapping("/denos")
    public ResponseEntity<DenoResponse> createDeno(@Valid @RequestBody DenoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masService.createDeno(request));
    }

    /**
     * Retourne une MAS par identifiant dans l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @return fiche MAS
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @GetMapping("/{id}")
    public ResponseEntity<MasResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(masService.findById(id));
    }

    /**
     * Crée une MAS dans l'atelier courant.
     *
     * @param request numéro, marque et statut d'utilisation
     * @return MAS créée
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si le numéro existe déjà
     */
    @PostMapping
    public ResponseEntity<MasResponse> create(@Valid @RequestBody MasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masService.create(request));
    }

    /**
     * Met à jour une MAS de l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @param request données mises à jour
     * @return MAS modifiée
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ;
     *         {@code 409} en cas de conflit de numéro
     */
    @PutMapping("/{id}")
    public ResponseEntity<MasResponse> update(@PathVariable Long id, @Valid @RequestBody MasRequest request) {
        return ResponseEntity.ok(masService.update(id, request));
    }

    /**
     * Supprime une MAS de l'atelier courant.
     *
     * @param id identifiant de la MAS
     * @return réponse vide ({@code 204})
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        masService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

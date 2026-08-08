package com.devicemanager.controller;

import com.devicemanager.dto.AtelierRequest;
import com.devicemanager.dto.AtelierResponsableDto;
import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.CasinoRequest;
import com.devicemanager.dto.CasinoSummary;
import com.devicemanager.dto.PreferredAtelierRequest;
import com.devicemanager.service.AtelierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrôleur REST de la hiérarchie organisationnelle casino → atelier.
 * <p>
 * Un casino possède un ou plusieurs ateliers ; chaque atelier regroupe les pièces
 * détachées, MAS, SFM et commandes. Le technicien ne voit que son atelier préféré ;
 * l'admin bascule via {@code X-Atelier-Id} ou l'endpoint {@code /preferred}.
 */
@RestController
@RequestMapping("/api/ateliers")
@RequiredArgsConstructor
public class AtelierController {

    private final AtelierService atelierService;

    /**
     * Liste les ateliers accessibles à l'utilisateur connecté.
     *
     * @param authentication utilisateur authentifié
     * @return ateliers du groupe (un seul pour un technicien)
     */
    @GetMapping
    public ResponseEntity<List<AtelierSummary>> list(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listForUser(authentication.getName()));
    }

    /**
     * Liste les casinos du groupe de l'administrateur connecté.
     *
     * @param authentication administrateur authentifié
     * @return casinos rattachés au même groupe
     */
    @GetMapping("/casinos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CasinoSummary>> listCasinos(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listCasinosForUser(authentication.getName()));
    }

    /**
     * Crée un casino dans le groupe de l'administrateur.
     *
     * @param authentication administrateur
     * @param request        nom du casino
     * @return casino créé
     */
    @PostMapping("/casinos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CasinoSummary> createCasino(
            Authentication authentication,
            @Valid @RequestBody CasinoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(atelierService.createCasino(authentication.getName(), request));
    }

    /**
     * Met à jour le nom d'un casino du groupe.
     *
     * @param authentication administrateur
     * @param id             casino
     * @param request        nouveau nom
     * @return casino modifié
     */
    @PutMapping("/casinos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CasinoSummary> updateCasino(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody CasinoRequest request) {
        return ResponseEntity.ok(atelierService.updateCasino(authentication.getName(), id, request));
    }

    /**
     * Supprime un casino sans atelier rattaché.
     *
     * @param authentication administrateur
     * @param id             casino
     */
    @DeleteMapping("/casinos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCasino(Authentication authentication, @PathVariable Long id) {
        atelierService.deleteCasino(authentication.getName(), id);
    }

    /**
     * Liste les utilisateurs du groupe pouvant être responsables d'atelier.
     *
     * @param authentication administrateur authentifié
     * @return utilisateurs du même groupe
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AtelierResponsableDto>> listUsers(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listUsersForGroupe(authentication.getName()));
    }

    /**
     * Mémorise l'atelier préféré de l'administrateur (contexte {@code X-Atelier-Id}).
     *
     * @param authentication administrateur authentifié
     * @param request identifiant de l'atelier à sélectionner
     * @return résumé de l'atelier choisi
     * @throws org.springframework.web.server.ResponseStatusException {@code 403} si l'atelier n'appartient pas au groupe
     */
    @PutMapping("/preferred")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtelierSummary> setPreferred(
            Authentication authentication,
            @Valid @RequestBody PreferredAtelierRequest request) {
        return ResponseEntity.ok(atelierService.setPreferredAtelier(authentication.getName(), request.getAtelierId()));
    }

    /**
     * Crée un nouvel atelier rattaché à un casino du groupe.
     *
     * @param authentication administrateur authentifié
     * @param request nom, casino, coordonnées et responsables
     * @return atelier créé
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si le nom existe déjà pour ce casino
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtelierSummary> create(
            Authentication authentication,
            @Valid @RequestBody AtelierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(atelierService.create(authentication.getName(), request));
    }

    /**
     * Met à jour un atelier existant du groupe.
     *
     * @param authentication administrateur authentifié
     * @param id identifiant de l'atelier
     * @param request données mises à jour
     * @return atelier modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 403} si hors groupe ;
     *         {@code 409} en cas de conflit de nom
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtelierSummary> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AtelierRequest request) {
        return ResponseEntity.ok(atelierService.update(authentication.getName(), id, request));
    }

    /**
     * Supprime un atelier sans pièces, MAS, SFM ni demandes liées.
     *
     * @param authentication administrateur authentifié
     * @param id identifiant de l'atelier
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si des données métier y sont rattachées
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        atelierService.delete(authentication.getName(), id);
    }
}

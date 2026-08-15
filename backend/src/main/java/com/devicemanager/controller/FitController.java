package com.devicemanager.controller;

import com.devicemanager.dto.FitFromMasRequest;
import com.devicemanager.dto.FitLigneRequest;
import com.devicemanager.dto.FitResponse;
import com.devicemanager.dto.FitSignatairesResponse;
import com.devicemanager.service.FitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fiches d'inventaire / intervention technique (FIT) par atelier.
 */
@RestController
@RequestMapping("/api/fit")
@RequiredArgsConstructor
public class FitController {

    private final FitService fitService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<FitResponse>> list(
            @RequestParam(required = false) Long masId) {
        if (masId != null) {
            return ResponseEntity.ok(
                    fitService.findOptionalByMasId(masId).map(List::of).orElseGet(List::of));
        }
        return ResponseEntity.ok(fitService.findAll());
    }

    /**
     * Combos signataires : admins et techniciens du groupe.
     * Déclaré avant {@code /{id}} pour éviter la collision de mapping.
     */
    @GetMapping("/signataires")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<FitSignatairesResponse> listSignataires(Authentication authentication) {
        return ResponseEntity.ok(fitService.listSignataires(authentication.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<FitResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(fitService.findById(id));
    }

    /**
     * Crée (ou récupère) la FIT d'une MAS.
     */
    @PostMapping("/from-mas")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<FitResponse> ensureFromMas(@Valid @RequestBody FitFromMasRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fitService.ensureForMas(request));
    }

    /**
     * Ajoute une ligne d'intervention signée (admin + technicien) sur une FIT.
     */
    @PostMapping("/{id}/lignes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<FitResponse> addLigne(
            @PathVariable Long id,
            @Valid @RequestBody FitLigneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fitService.addLigne(id, request));
    }
}

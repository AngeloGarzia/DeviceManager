package com.devicemanager.controller;

import com.devicemanager.dto.InterventionRequest;
import com.devicemanager.dto.InterventionResponse;
import com.devicemanager.service.InterventionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bons d'intervention : consommation de pièces détachées et archive atelier.
 */
@RestController
@RequestMapping("/api/interventions")
@RequiredArgsConstructor
public class InterventionController {

    private final InterventionService interventionService;

    /**
     * Crée et archive un bon d'intervention (décrémente le stock).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<InterventionResponse> create(
            @Valid @RequestBody InterventionRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interventionService.create(request, authentication.getName()));
    }

    /**
     * Liste les bons d'intervention de l'atelier courant.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<InterventionResponse>> list() {
        return ResponseEntity.ok(interventionService.findAll());
    }

    /**
     * Détail d'un bon d'intervention archivé.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<InterventionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(interventionService.findById(id));
    }
}

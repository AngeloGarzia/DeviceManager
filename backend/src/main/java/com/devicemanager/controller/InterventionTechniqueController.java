package com.devicemanager.controller;

import com.devicemanager.dto.InterventionTechniqueRequest;
import com.devicemanager.dto.InterventionTechniqueResponse;
import com.devicemanager.service.InterventionTechniqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Interventions techniques libres sur MAS (table {@code interventions}).
 */
@RestController
@RequestMapping("/api/interventions-techniques")
@RequiredArgsConstructor
public class InterventionTechniqueController {

    private final InterventionTechniqueService interventionTechniqueService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<InterventionTechniqueResponse>> create(
            @Valid @RequestBody InterventionTechniqueRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interventionTechniqueService.create(request, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<InterventionTechniqueResponse>> list(
            @RequestParam(required = false) Long masId) {
        if (masId != null) {
            return ResponseEntity.ok(interventionTechniqueService.findByMasId(masId));
        }
        return ResponseEntity.ok(interventionTechniqueService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<InterventionTechniqueResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(interventionTechniqueService.findById(id));
    }
}

package com.devicemanager.controller;

import com.devicemanager.dto.VisiteQuadriObligationResponse;
import com.devicemanager.dto.VisiteQuadriRequest;
import com.devicemanager.dto.VisiteQuadriResponse;
import com.devicemanager.service.VisiteQuadriService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Visites quadritrimestrielles SFM × marque (tous les 4 mois).
 */
@RestController
@RequestMapping("/api/visites-quadri")
@RequiredArgsConstructor
public class VisiteQuadriController {

    private final VisiteQuadriService visiteQuadriService;

    /**
     * Obligations courantes avec échéances et niveaux d'alerte.
     */
    @GetMapping("/status")
    public ResponseEntity<List<VisiteQuadriObligationResponse>> status() {
        return ResponseEntity.ok(visiteQuadriService.status());
    }

    /**
     * Nombre d'obligations en alerte (WARN ou OVERDUE) — badge menu MAS.
     */
    @GetMapping("/warning-count")
    public ResponseEntity<Map<String, Long>> warningCount() {
        return ResponseEntity.ok(Map.of("count", visiteQuadriService.warningCount()));
    }

    /**
     * Historique des visites (filtres optionnels).
     */
    @GetMapping
    public ResponseEntity<List<VisiteQuadriResponse>> history(
            @RequestParam(required = false) Long sfmId,
            @RequestParam(required = false) Long marqueId) {
        return ResponseEntity.ok(visiteQuadriService.history(sfmId, marqueId));
    }

    /**
     * Enregistre une visite pour un couple SFM × marque.
     */
    @PostMapping
    public ResponseEntity<VisiteQuadriResponse> create(
            @Valid @RequestBody VisiteQuadriRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visiteQuadriService.create(request, username));
    }
}

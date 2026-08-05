package com.devicemanager.controller;

import com.devicemanager.dto.AdminLogListResponse;
import com.devicemanager.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation des logs applicatifs SLF4J — réservée aux administrateurs.
 * <p>
 * Sécurisé via {@code SecurityConfig} ({@code /api/logs/**} → rôle ADMIN).
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final AdminLogService adminLogService;

    /**
     * Retourne les derniers logs capturés en mémoire.
     *
     * @param level  niveau minimum (TRACE, DEBUG, INFO, WARN, ERROR)
     * @param logger filtre sur le nom de logger
     * @param q      recherche dans le message / stack
     * @param limit  nombre max d'entrées (défaut 200)
     * @return snapshot filtré
     */
    @GetMapping
    public ResponseEntity<AdminLogListResponse> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String logger,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(adminLogService.list(level, logger, q, limit));
    }

    /**
     * Vide le tampon de logs en mémoire.
     *
     * @return 204 No Content
     */
    @DeleteMapping
    public ResponseEntity<Void> clear() {
        adminLogService.clear();
        return ResponseEntity.noContent().build();
    }
}

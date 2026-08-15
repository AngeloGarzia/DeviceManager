package com.devicemanager.controller;

import com.devicemanager.dto.TimelineEventResponse;
import com.devicemanager.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Timeline agrégée : commandes, bons, interventions techniques, FIT, stock.
 * Chaque événement porte une {@code column} d'abscisse (swimlane).
 */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    /**
     * Liste les événements de l'atelier courant (du plus récent au plus ancien).
     *
     * @param from  borne basse inclusive (ISO-8601 local)
     * @param to    borne haute inclusive
     * @param types types à inclure (répétable ou CSV)
     * @param masId si renseigné, timeline filtrée sur cette MAS (bons / interventions / FIT)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<TimelineEventResponse>> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) Long masId) {
        return ResponseEntity.ok(timelineService.findEvents(from, to, types, masId));
    }
}

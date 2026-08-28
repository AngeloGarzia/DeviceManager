package com.devicemanager.controller;

import com.devicemanager.dto.ArretMaintenanceAlertResponse;
import com.devicemanager.dto.ArretMaintenanceRepriseRequest;
import com.devicemanager.dto.ArretMaintenanceRequest;
import com.devicemanager.dto.ArretMaintenanceResponse;
import com.devicemanager.service.ArretMaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/arrets-maintenance")
@RequiredArgsConstructor
public class ArretMaintenanceController {

    private final ArretMaintenanceService arretMaintenanceService;

    @GetMapping("/active")
    public ResponseEntity<List<ArretMaintenanceResponse>> listActive() {
        return ResponseEntity.ok(arretMaintenanceService.listActive());
    }

    @GetMapping("/alert")
    public ResponseEntity<ArretMaintenanceAlertResponse> alert() {
        return ResponseEntity.ok(arretMaintenanceService.alertSummary());
    }

    @GetMapping
    public ResponseEntity<List<ArretMaintenanceResponse>> history() {
        return ResponseEntity.ok(arretMaintenanceService.history());
    }

    @PostMapping
    public ResponseEntity<ArretMaintenanceResponse> declareArret(
            @Valid @RequestBody ArretMaintenanceRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(arretMaintenanceService.declareArret(request, username));
    }

    @PutMapping("/{id}/reprise")
    public ResponseEntity<ArretMaintenanceResponse> declareReprise(
            @PathVariable Long id,
            @Valid @RequestBody ArretMaintenanceRepriseRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(arretMaintenanceService.declareReprise(id, request, username));
    }
}

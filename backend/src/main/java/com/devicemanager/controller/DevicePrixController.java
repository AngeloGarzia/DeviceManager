package com.devicemanager.controller;

import com.devicemanager.dto.AiDevisPrixConfirmRequest;
import com.devicemanager.dto.AiDevisPrixConfirmResponse;
import com.devicemanager.dto.AiDevisPrixScanResponse;
import com.devicemanager.dto.DevicePrixAlerteResponse;
import com.devicemanager.dto.DevicePrixObservationResponse;
import com.devicemanager.service.DevicePrixService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Historique des prix pièces et alertes d'incohérence (issus des devis).
 */
@RestController
@RequiredArgsConstructor
public class DevicePrixController {

    private final DevicePrixService devicePrixService;

    @GetMapping("/api/devices/{id}/prix-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<DevicePrixObservationResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(devicePrixService.history(id));
    }

    @GetMapping("/api/prix-alertes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DevicePrixAlerteResponse>> listAlertes(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(devicePrixService.listAlertes(status));
    }

    @PostMapping("/api/prix-alertes/{id}/ack")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DevicePrixAlerteResponse> acknowledge(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(devicePrixService.acknowledge(id, authentication.getName()));
    }

    @PostMapping("/api/prix-alertes/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DevicePrixAlerteResponse> dismiss(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(devicePrixService.dismiss(id, authentication.getName()));
    }

    @PostMapping(value = "/api/order-requests/{id}/devis/analyze-prices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiDevisPrixScanResponse> analyzePrices(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(devicePrixService.analyzeDevisPrices(id, file));
    }

    @PostMapping("/api/order-requests/{id}/devis/confirm-prices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiDevisPrixConfirmResponse> confirmPrices(
            @PathVariable Long id,
            @Valid @RequestBody AiDevisPrixConfirmRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                devicePrixService.confirmDevisPrices(id, request, authentication.getName()));
    }
}

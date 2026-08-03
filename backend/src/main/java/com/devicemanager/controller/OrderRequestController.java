package com.devicemanager.controller;

import com.devicemanager.dto.MailPreviewItem;
import com.devicemanager.dto.OrderRequestDto;
import com.devicemanager.dto.OrderRequestResponse;
import com.devicemanager.service.OrderRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order-requests")
@RequiredArgsConstructor
public class OrderRequestController {

    private final OrderRequestService orderRequestService;

    /** Création d'une demande — admin et technicien. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<OrderRequestResponse> create(
            @Valid @RequestBody OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderRequestService.create(request, authentication.getName()));
    }

    /** Consultation — admin et technicien. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<OrderRequestResponse>> list() {
        return ResponseEntity.ok(orderRequestService.findAll());
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return ResponseEntity.ok(Map.of("count", orderRequestService.countPending()));
    }

    @PostMapping("/mail-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<MailPreviewItem>> previewCreate(
            @RequestBody OrderRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.previewCreateMails(request, authentication.getName()));
    }

    /** Aperçu des e-mails SFM (consultation) — admin et technicien. */
    @GetMapping("/{id}/mail-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIEN')")
    public ResponseEntity<List<MailPreviewItem>> previewValidate(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.previewSfmMails(id, authentication.getName()));
    }

    /** Validation / envoi SFM — admin uniquement. */
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderRequestResponse> validate(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(orderRequestService.validate(id, authentication.getName()));
    }

    /** Suppression — admin uniquement. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        orderRequestService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

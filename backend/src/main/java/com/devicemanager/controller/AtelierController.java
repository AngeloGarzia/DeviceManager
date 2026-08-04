package com.devicemanager.controller;

import com.devicemanager.dto.AtelierRequest;
import com.devicemanager.dto.AtelierResponsableDto;
import com.devicemanager.dto.AtelierSummary;
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

@RestController
@RequestMapping("/api/ateliers")
@RequiredArgsConstructor
public class AtelierController {

    private final AtelierService atelierService;

    @GetMapping
    public ResponseEntity<List<AtelierSummary>> list(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listForUser(authentication.getName()));
    }

    @GetMapping("/casinos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CasinoSummary>> listCasinos(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listCasinosForUser(authentication.getName()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AtelierResponsableDto>> listUsers(Authentication authentication) {
        return ResponseEntity.ok(atelierService.listUsersForGroupe(authentication.getName()));
    }

    @PutMapping("/preferred")
    public ResponseEntity<AtelierSummary> setPreferred(
            Authentication authentication,
            @Valid @RequestBody PreferredAtelierRequest request) {
        return ResponseEntity.ok(atelierService.setPreferredAtelier(authentication.getName(), request.getAtelierId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtelierSummary> create(
            Authentication authentication,
            @Valid @RequestBody AtelierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(atelierService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtelierSummary> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AtelierRequest request) {
        return ResponseEntity.ok(atelierService.update(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        atelierService.delete(authentication.getName(), id);
    }
}

package com.devicemanager.controller;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.PreferredAtelierRequest;
import com.devicemanager.service.AtelierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PutMapping("/preferred")
    public ResponseEntity<AtelierSummary> setPreferred(
            Authentication authentication,
            @Valid @RequestBody PreferredAtelierRequest request) {
        return ResponseEntity.ok(atelierService.setPreferredAtelier(authentication.getName(), request.getAtelierId()));
    }
}

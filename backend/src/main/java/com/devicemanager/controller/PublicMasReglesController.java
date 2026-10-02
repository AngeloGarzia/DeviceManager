package com.devicemanager.controller;

import com.devicemanager.dto.PublicMasReglesResponse;
import com.devicemanager.service.PublicMasReglesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publique (anonyme) : consultation lecture seule des règles de jeux via jeton QR.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMasReglesController {

    private final PublicMasReglesService publicMasReglesService;

    @GetMapping("/r/{token}")
    public ResponseEntity<PublicMasReglesResponse> byToken(@PathVariable String token) {
        return ResponseEntity.ok(publicMasReglesService.findByToken(token));
    }

    @GetMapping("/r/{token}/regles/{regleId}/file")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String token,
            @PathVariable Long regleId) {
        return publicMasReglesService.downloadRegleFile(token, regleId);
    }
}

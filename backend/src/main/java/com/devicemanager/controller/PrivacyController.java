package com.devicemanager.controller;

import com.devicemanager.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposition publique des mentions / champs RGPD éditables (page /confidentialite).
 */
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private final AppSettingsService appSettingsService;

    /**
     * Retourne les champs RGPD renseignés dans Setup (valeurs vides = à compléter).
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> publicPolicy() {
        return ResponseEntity.ok(appSettingsService.publicPrivacyValues());
    }
}

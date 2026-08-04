package com.devicemanager.controller;

import com.devicemanager.dto.AppSettingResponse;
import com.devicemanager.dto.AppSettingsUpdateRequest;
import com.devicemanager.dto.MailTestResponse;
import com.devicemanager.service.AppSettingsService;
import com.devicemanager.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST de configuration applicative (Setup).
 * <p>
 * Expose les paramètres globaux DeviceManager : messagerie, JWT, CORS, stockage
 * et assistant IA. Indépendant du contexte atelier ({@code X-Atelier-Id}).
 */
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final AppSettingsService appSettingsService;
    private final MailService mailService;

    /**
     * Liste tous les paramètres applicatifs (secrets masqués).
     *
     * @return réglages triés par catégorie et libellé
     */
    @GetMapping
    public ResponseEntity<List<AppSettingResponse>> list() {
        return ResponseEntity.ok(appSettingsService.list());
    }

    /**
     * Met à jour les paramètres applicatifs fournis.
     *
     * @param request paires clé/valeur à enregistrer
     * @return liste actualisée des paramètres
     */
    @PutMapping
    public ResponseEntity<List<AppSettingResponse>> update(@Valid @RequestBody AppSettingsUpdateRequest request) {
        return ResponseEntity.ok(appSettingsService.update(request));
    }

    /**
     * Envoie un e-mail de test SMTP vers l'adresse administrateur configurée.
     *
     * @return résultat du test (succès ou message d'erreur)
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si la config SMTP est incomplète ;
     *         {@code 502} en cas d'échec d'envoi
     */
    @PostMapping("/mail/test")
    public ResponseEntity<MailTestResponse> testMail() {
        return ResponseEntity.ok(mailService.sendTestEmail());
    }
}

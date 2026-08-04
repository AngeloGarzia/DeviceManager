package com.devicemanager.controller;

import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST d'authentification pour DeviceManager.
 * <p>
 * Gère la connexion JWT des administrateurs et techniciens des ateliers casino
 * (gestion de pièces détachées). La réponse inclut les ateliers accessibles et
 * l'atelier actif initial ; le contexte multi-tenant est ensuite porté par
 * l'en-tête {@code X-Atelier-Id}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authentifie un utilisateur et retourne un jeton JWT avec le profil et les ateliers disponibles.
     *
     * @param request identifiants de connexion (nom d'utilisateur et mot de passe)
     * @return réponse contenant le jeton, le rôle, le groupe et l'atelier par défaut
     * @throws org.springframework.web.server.ResponseStatusException {@code 401} si les identifiants sont invalides
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}

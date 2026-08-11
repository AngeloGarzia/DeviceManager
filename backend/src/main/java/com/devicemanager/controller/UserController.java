package com.devicemanager.controller;

import com.devicemanager.dto.UserRequest;
import com.devicemanager.dto.UserResponse;
import com.devicemanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST de gestion des comptes utilisateurs.
 * <p>
 * Administration des administrateurs et techniciens d'un groupe casino/atelier.
 * Les nouveaux comptes héritent du groupe de l'atelier courant ({@code X-Atelier-Id}).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Liste les utilisateurs du groupe de l'administrateur connecté.
     *
     * @return comptes du groupe, triés par nom d'utilisateur
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Retourne un utilisateur du groupe courant par identifiant.
     *
     * @param id identifiant du compte
     * @return profil utilisateur
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ou hors groupe
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * Crée un utilisateur rattaché au groupe de l'atelier courant.
     *
     * @param request identité, rôle, mot de passe et atelier préféré éventuel
     * @return compte créé
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si identifiant ou e-mail en doublon ;
     *         {@code 400} si mot de passe ou atelier technicien manquant
     */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    /**
     * Met à jour un utilisateur existant.
     *
     * @param id identifiant du compte
     * @param request données mises à jour (mot de passe optionnel)
     * @return compte modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si suppression du dernier admin ;
     *         {@code 409} en cas de conflit identifiant/e-mail
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * Supprime un utilisateur (interdit sur soi-même et sur le dernier administrateur).
     *
     * @param id identifiant du compte à supprimer
     * @param authentication utilisateur connecté effectuant la suppression
     * @return réponse vide ({@code 204})
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si auto-suppression ou dernier admin
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

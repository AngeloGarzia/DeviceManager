package com.devicemanager.service;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service d'authentification JWT pour DeviceManager.
 * <p>
 * Valide les identifiants, émet un jeton et renvoie le profil utilisateur avec
 * la liste des ateliers casino accessibles et l'atelier actif initial pour
 * le contexte multi-tenant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AtelierService atelierService;

    /**
     * Authentifie un utilisateur et construit la réponse de connexion.
     *
     * @param request identifiants (nom d'utilisateur et mot de passe)
     * @return jeton JWT, profil, groupe et ateliers disponibles
     * @throws org.springframework.web.server.ResponseStatusException {@code 401} si identifiants invalides
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String attemptedUsername = request.getUsername() == null ? "" : request.getUsername().trim();
        User user = userRepository.findByUsername(attemptedUsername).orElse(null);
        if (user == null) {
            log.warn("Connexion refusée (utilisateur inconnu) username={}", attemptedUsername);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Connexion refusée (mot de passe invalide) utilisateur={} rôle={}",
                    user.getUsername(), user.getRole());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        List<AtelierSummary> ateliers = atelierService.listForUser(user.getUsername());
        Long atelierId = resolveLoginAtelierId(user, ateliers);

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        log.info("Connexion réussie utilisateur={} rôle={} atelier={} groupe={}",
                user.getUsername(),
                user.getRole(),
                atelierId,
                user.getGroupe() != null ? user.getGroupe().getNom() : null);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .username(user.getUsername())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .role(user.getRole())
                .groupeId(user.getGroupe() != null ? user.getGroupe().getId() : null)
                .groupeNom(user.getGroupe() != null ? user.getGroupe().getNom() : null)
                .atelierId(atelierId)
                .ateliers(ateliers)
                .build();
    }

    /**
     * Préfère l'atelier mémorisé s'il est encore autorisé pour le groupe ; sinon le premier de la liste.
     */
    private Long resolveLoginAtelierId(User user, List<AtelierSummary> ateliers) {
        if (ateliers.isEmpty()) {
            return null;
        }
        Long preferredId = user.getPreferredAtelier() != null ? user.getPreferredAtelier().getId() : null;
        if (preferredId != null
                && ateliers.stream().anyMatch(a -> preferredId.equals(a.getId()))) {
            return preferredId;
        }
        return ateliers.get(0).getId();
    }
}

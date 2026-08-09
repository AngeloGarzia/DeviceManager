package com.devicemanager.service;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.ChangePasswordRequest;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.entity.RefreshToken;
import com.devicemanager.entity.User;
import com.devicemanager.repository.RefreshTokenRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Service d'authentification JWT pour DeviceManager.
 * <p>
 * Valide les identifiants, émet un jeton d'accès et un refresh token, et renvoie le profil
 * utilisateur avec la liste des ateliers casino accessibles et l'atelier actif initial pour
 * le contexte multi-tenant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AtelierService atelierService;

    /**
     * Résultat de login / refresh : corps API + valeur brute du refresh token (cookie).
     */
    public record AuthSession(AuthResponse response, String refreshToken) {
    }

    /**
     * Authentifie un utilisateur et construit la session (access + refresh).
     *
     * @param request identifiants (nom d'utilisateur et mot de passe)
     * @return jeton JWT, refresh brut, profil, groupe et ateliers disponibles
     * @throws org.springframework.web.server.ResponseStatusException {@code 401} si identifiants invalides
     */
    @Transactional
    public AuthSession login(LoginRequest request) {
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

        AuthSession session = issueSession(user);
        log.info("Connexion réussie utilisateur={} rôle={} atelier={} groupe={}",
                user.getUsername(),
                user.getRole(),
                session.response().getAtelierId(),
                user.getGroupe() != null ? user.getGroupe().getNom() : null);
        return session;
    }

    /**
     * Rotation du refresh token : révoque l'ancien, émet un nouvel access + refresh.
     *
     * @param rawRefreshToken valeur brute du cookie {@code dm_refresh}
     * @return nouvelle session
     */
    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        RefreshToken existing = requireActiveRefreshToken(rawRefreshToken);
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issueSession(existing.getUser());
    }

    /**
     * Révoque le refresh token présenté (logout).
     *
     * @param rawRefreshToken valeur brute du cookie, ou {@code null}
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findActiveByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Change le mot de passe de l'utilisateur authentifié et lève {@code mustChangePassword}.
     *
     * @param username nom d'utilisateur authentifié
     * @param request  mot de passe actuel et nouveau
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("Mot de passe changé pour utilisateur={}", username);
    }

    private AuthSession issueSession(User user) {
        List<AtelierSummary> ateliers = atelierService.listForUser(user.getUsername());
        Long atelierId = resolveLoginAtelierId(user, ateliers);

        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole());
        String refreshRaw = jwtService.generateRefreshTokenValue();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(refreshRaw))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        AuthResponse response = AuthResponse.builder()
                .token(accessToken)
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
                .mustChangePassword(user.isMustChangePassword())
                .build();
        return new AuthSession(response, refreshRaw);
    }

    private RefreshToken requireActiveRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token manquant");
        }
        String hash = jwtService.hashToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findActiveByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré");
        }
        return token;
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

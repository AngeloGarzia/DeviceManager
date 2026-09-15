package com.devicemanager.service;

import com.devicemanager.dto.AtelierSummary;
import com.devicemanager.dto.AuthResponse;
import com.devicemanager.dto.ChangePasswordRequest;
import com.devicemanager.dto.LoginRequest;
import com.devicemanager.dto.MessageResponse;
import com.devicemanager.entity.PasswordResetToken;
import com.devicemanager.entity.RefreshToken;
import com.devicemanager.entity.User;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.mail.templates.PasswordResetEmail;
import com.devicemanager.repository.PasswordResetTokenRepository;
import com.devicemanager.repository.RefreshTokenRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import com.devicemanager.security.LoginAccountLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
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

    static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    static final String FORGOT_PASSWORD_MESSAGE =
            "Si un compte est associé à cette adresse, un e-mail de réinitialisation a été envoyé.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AtelierService atelierService;
    private final LoginAccountLockoutService accountLockoutService;
    private final TransactionalMail transactionalMail;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

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
        accountLockoutService.assertNotLocked(attemptedUsername);

        User user = userRepository.findByUsername(attemptedUsername).orElse(null);
        if (user == null) {
            accountLockoutService.recordFailure(attemptedUsername);
            log.warn("Connexion refusée (utilisateur inconnu) username={}", attemptedUsername);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            accountLockoutService.recordFailure(attemptedUsername);
            log.warn("Connexion refusée (mot de passe invalide) utilisateur={} rôle={}",
                    user.getUsername(), user.getRole());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        accountLockoutService.reset(attemptedUsername);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expirée. Veuillez vous reconnecter."));
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

    /**
     * Enregistre l'acceptation des mentions RGPD pour l'utilisateur authentifié.
     *
     * @param username nom d'utilisateur authentifié
     */
    @Transactional
    public void acceptPrivacy(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expirée. Veuillez vous reconnecter."));
        if (user.getPrivacyAcceptedAt() == null) {
            user.setPrivacyAcceptedAt(Instant.now());
            userRepository.save(user);
            log.info("Acceptation RGPD enregistrée pour utilisateur={}", username);
        }
    }

    /**
     * Demande un lien de réinitialisation. Réponse toujours générique (pas d'énumération d'e-mails).
     *
     * @param email adresse saisie par l'utilisateur
     * @return message générique
     */
    @Transactional
    public MessageResponse requestPasswordReset(String email) {
        MessageResponse ok = MessageResponse.builder().message(FORGOT_PASSWORD_MESSAGE).build();
        String normalized = email == null ? "" : email.trim();
        if (normalized.isBlank()) {
            return ok;
        }

        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) {
            log.info("Demande reset mot de passe ignorée (e-mail inconnu)");
            return ok;
        }

        String baseUrl = resolveFrontendBaseUrl();
        if (baseUrl == null) {
            log.warn("Reset mot de passe impossible : APP_FRONTEND_BASE_URL / CORS non configurés");
            return ok;
        }

        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId());

        String rawToken = jwtService.generateRefreshTokenValue();
        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(Instant.now().plus(PASSWORD_RESET_TTL))
                .used(false)
                .build();
        passwordResetTokenRepository.save(entity);

        String resetUrl = baseUrl + "/reset-password?token=" + rawToken;
        transactionalMail.sendPasswordReset(
                user.getEmail(),
                new PasswordResetEmail.Context(user.getPrenom(), resetUrl));
        log.info("Lien reset mot de passe généré pour utilisateur={}", user.getUsername());
        return ok;
    }

    /**
     * Applique un nouveau mot de passe via jeton reçu par e-mail.
     *
     * @param rawToken    jeton opaque
     * @param newPassword nouveau mot de passe en clair
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lien de réinitialisation invalide ou expiré.");
        }
        String hash = jwtService.hashToken(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findActiveByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Lien de réinitialisation invalide ou expiré."));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lien de réinitialisation invalide ou expiré.");
        }

        User user = token.getUser();
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit être différent");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("Mot de passe réinitialisé pour utilisateur={}", user.getUsername());
    }

    private String resolveFrontendBaseUrl() {
        String configured = trimTrailingSlash(frontendBaseUrl);
        if (configured != null) {
            return configured;
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return null;
        }
        for (String part : corsAllowedOrigins.split(",")) {
            String origin = trimTrailingSlash(part);
            if (origin != null) {
                return origin;
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
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
                .mustAcceptPrivacy(user.getPrivacyAcceptedAt() == null)
                .build();
        return new AuthSession(response, refreshRaw);
    }

    private RefreshToken requireActiveRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expirée. Veuillez vous reconnecter.");
        }
        String hash = jwtService.hashToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findActiveByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session invalide. Veuillez vous reconnecter."));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expirée. Veuillez vous reconnecter.");
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

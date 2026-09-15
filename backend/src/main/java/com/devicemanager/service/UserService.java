package com.devicemanager.service;

import com.devicemanager.dto.MessageResponse;
import com.devicemanager.dto.UserRequest;
import com.devicemanager.dto.UserResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Groupe;
import com.devicemanager.entity.PasswordResetToken;
import com.devicemanager.entity.User;
import com.devicemanager.mail.TransactionalMail;
import com.devicemanager.mail.templates.PasswordWelcomeEmail;
import com.devicemanager.repository.PasswordResetTokenRepository;
import com.devicemanager.repository.RefreshTokenRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.JwtService;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service de gestion des comptes utilisateurs DeviceManager.
 * <p>
 * Administration des administrateurs et techniciens d'un groupe casino ;
 * toutes les opérations sont scopées au groupe de l'administrateur connecté.
 * Les créations héritent du groupe de l'atelier courant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(Roles.ADMIN, Roles.TECHNICIEN);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AtelierService atelierService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final TransactionalMail transactionalMail;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    /**
     * Liste les utilisateurs du groupe de l'administrateur connecté.
     *
     * @return comptes du groupe, triés par nom d'utilisateur
     */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        Long groupeId = requireActorGroupeId();
        return userRepository.findAllByGroupeId(groupeId).stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retourne un utilisateur du groupe courant par identifiant.
     *
     * @param id identifiant du compte
     * @return profil utilisateur
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable ou hors groupe
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(getInActorGroupe(id));
    }

    /**
     * Crée un utilisateur rattaché au groupe de l'atelier courant.
     *
     * @param request identité, rôle, mot de passe et atelier préféré
     * @return compte créé
     * @throws org.springframework.web.server.ResponseStatusException {@code 409} si identifiant ou e-mail en doublon ;
     *         {@code 400} si mot de passe ou atelier technicien manquant
     */
    public UserResponse create(UserRequest request) {
        String username = request.getUsername().trim();
        String email = requireEmail(request.getEmail());
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà utilisé");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail déjà utilisé");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe obligatoire");
        }
        String role = normalizeRole(request.getRole());
        User actor = requireActor();
        Groupe groupe = atelierService.requireCurrentAtelier().getCasino().getGroupe();
        ensureActorOwnsGroupe(actor, groupe.getId());
        Atelier preferred = resolvePreferredAtelier(actor, role, request.getPreferredAtelierId());
        User saved = userRepository.save(User.builder()
                .username(username)
                .nom(requireName(request.getNom(), "Nom"))
                .prenom(requireName(request.getPrenom(), "Prénom"))
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .groupe(groupe)
                .preferredAtelier(preferred)
                .build());
        log.info("Création en base — Utilisateur id={} username={} {} {} <{}> rôle={} atelierPréféré={} groupe={}",
                saved.getId(),
                saved.getUsername(),
                saved.getPrenom(),
                saved.getNom(),
                saved.getEmail(),
                saved.getRole(),
                preferred != null ? preferred.getId() : null,
                groupe.getId());
        return toResponse(saved);
    }

    /**
     * Met à jour un utilisateur du groupe de l'administrateur connecté.
     *
     * @param id identifiant du compte
     * @param request données mises à jour (mot de passe optionnel)
     * @return compte modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si dernier admin retiré ;
     *         {@code 409} en cas de conflit identifiant/e-mail ;
     *         {@code 404} si hors groupe
     */
    public UserResponse update(Long id, UserRequest request) {
        User user = getInActorGroupe(id);
        String username = request.getUsername().trim();
        String email = requireEmail(request.getEmail());
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà utilisé");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail déjà utilisé");
        }

        String newRole = normalizeRole(request.getRole());
        Long groupeId = requireGroupeId(user);
        if (Roles.ADMIN.equals(user.getRole()) && !Roles.ADMIN.equals(newRole) && countAdminsInGroupe(groupeId) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de retirer le dernier administrateur");
        }

        User actor = requireActor();
        Atelier preferred = resolvePreferredAtelier(actor, newRole, request.getPreferredAtelierId());

        user.setUsername(username);
        user.setNom(requireName(request.getNom(), "Nom"));
        user.setPrenom(requireName(request.getPrenom(), "Prénom"));
        user.setEmail(email);
        user.setRole(newRole);
        user.setPreferredAtelier(preferred);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        User saved = userRepository.saveAndFlush(user);
        log.info("Modification en base — Utilisateur id={} username={} {} {} <{}> rôle={} atelierPréféré={}",
                saved.getId(),
                saved.getUsername(),
                saved.getPrenom(),
                saved.getNom(),
                saved.getEmail(),
                saved.getRole(),
                preferred != null ? preferred.getId() : null);
        return toResponse(saved);
    }

    /**
     * Supprime un utilisateur du groupe (interdit sur soi-même et sur le dernier administrateur du groupe).
     *
     * @param id identifiant du compte
     * @param currentUsername utilisateur connecté
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si auto-suppression ou dernier admin ;
     *         {@code 404} si hors groupe
     */
    public void delete(Long id, String currentUsername) {
        User user = getInActorGroupe(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte");
        }
        Long groupeId = requireGroupeId(user);
        if (Roles.ADMIN.equals(user.getRole()) && countAdminsInGroupe(groupeId) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de supprimer le dernier administrateur");
        }
        String username = user.getUsername();
        String role = user.getRole();
        userRepository.delete(user);
        log.info("Suppression en base — Utilisateur id={} username={} rôle={} par={} groupe={}",
                id, username, role, currentUsername, groupeId);
    }

    /**
     * Envoie un e-mail de bienvenue : identifiant, mot de passe temporaire et lien de reset.
     *
     * @param id identifiant du compte cible
     * @return message de confirmation
     */
    public MessageResponse sendWelcomeMail(Long id) {
        User user = getInActorGroupe(id);
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cet utilisateur n'a pas d'adresse e-mail.");
        }

        String baseUrl = resolveFrontendBaseUrl();
        if (baseUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL frontend manquante (APP_FRONTEND_BASE_URL ou CORS). Impossible d'envoyer le lien.");
        }

        String temporaryPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        passwordResetTokenRepository.invalidateUnusedByUserId(user.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());

        String rawToken = jwtService.generateRefreshTokenValue();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(Instant.now().plus(PASSWORD_RESET_TTL))
                .used(false)
                .build());

        String resetUrl = baseUrl + "/reset-password?token=" + rawToken;
        transactionalMail.sendUserWelcome(
                email,
                new PasswordWelcomeEmail.Context(
                        user.getPrenom(),
                        user.getUsername(),
                        temporaryPassword,
                        resetUrl));

        log.info("E-mail de bienvenue envoyé à utilisateur={} <{}>", user.getUsername(), email);
        return MessageResponse.builder()
                .message("E-mail de bienvenue envoyé à " + email)
                .build();
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
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

    private User requireActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise."));
    }

    private Long requireActorGroupeId() {
        return requireGroupeId(requireActor());
    }

    private Long requireGroupeId(User user) {
        if (user.getGroupe() == null || user.getGroupe().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Votre compte n'est rattaché à aucun groupe. Contactez un administrateur.");
        }
        return user.getGroupe().getId();
    }

    private void ensureActorOwnsGroupe(User actor, Long groupeId) {
        Long actorGroupeId = requireGroupeId(actor);
        if (!Objects.equals(actorGroupeId, groupeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Groupe non autorisé pour ce compte");
        }
    }

    private User getInActorGroupe(Long id) {
        User actor = requireActor();
        Long actorGroupeId = requireGroupeId(actor);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        Long targetGroupeId = user.getGroupe() != null ? user.getGroupe().getId() : null;
        if (!Objects.equals(actorGroupeId, targetGroupeId)) {
            // 404 volontaire : ne pas révéler l'existence d'un compte hors groupe.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
        return user;
    }

    private Atelier resolvePreferredAtelier(User actor, String role, Long preferredAtelierId) {
        if (Roles.TECHNICIEN.equals(role)) {
            if (preferredAtelierId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Atelier préféré obligatoire pour un technicien");
            }
            Atelier atelier = atelierService.requireAtelierForUserGroupe(actor, preferredAtelierId);
            requireUtilise(atelier);
            return atelier;
        }
        if (preferredAtelierId == null) {
            return null;
        }
        Atelier atelier = atelierService.requireAtelierForUserGroupe(actor, preferredAtelierId);
        requireUtilise(atelier);
        return atelier;
    }

    private static void requireUtilise(Atelier atelier) {
        if (!atelier.isUtilise()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cet atelier est marqué « non utilisé » et ne peut pas être affecté.");
        }
    }

    private long countAdminsInGroupe(Long groupeId) {
        return userRepository.findAllByGroupeId(groupeId).stream()
                .filter(u -> Roles.ADMIN.equals(u.getRole()))
                .count();
    }

    private String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " obligatoire");
        }
        return value.trim();
    }

    private String requireEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail obligatoire");
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if ("TECH".equals(normalized)) {
            normalized = Roles.TECHNICIEN;
        }
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rôle invalide. Choisissez Administrateur ou Technicien.");
        }
        return normalized;
    }

    private UserResponse toResponse(User user) {
        Atelier preferred = user.getPreferredAtelier();
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .role(user.getRole())
                .preferredAtelierId(preferred != null ? preferred.getId() : null)
                .preferredAtelierNom(preferred != null ? preferred.getNom() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

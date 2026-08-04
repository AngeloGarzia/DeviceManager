package com.devicemanager.service;

import com.devicemanager.dto.UserRequest;
import com.devicemanager.dto.UserResponse;
import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Service de gestion des comptes utilisateurs DeviceManager.
 * <p>
 * Administration des administrateurs et techniciens d'un groupe casino ;
 * les créations héritent du groupe de l'atelier courant ({@code X-Atelier-Id}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(Roles.ADMIN, Roles.TECHNICIEN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AtelierService atelierService;

    /**
     * Liste tous les utilisateurs triés par nom d'utilisateur.
     *
     * @return comptes avec rôle et atelier préféré
     */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retourne un utilisateur par identifiant.
     *
     * @param id identifiant du compte
     * @return profil utilisateur
     * @throws org.springframework.web.server.ResponseStatusException {@code 404} si introuvable
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(get(id));
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
        Atelier preferred = resolvePreferredAtelier(actor, role, request.getPreferredAtelierId());
        User saved = userRepository.save(User.builder()
                .username(username)
                .nom(requireName(request.getNom(), "Nom"))
                .prenom(requireName(request.getPrenom(), "Prénom"))
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .groupe(atelierService.requireCurrentAtelier().getCasino().getGroupe())
                .preferredAtelier(preferred)
                .build());
        log.info("Utilisateur créé: {} {} {} <{}> ({}) atelierPréféré={}",
                saved.getPrenom(), saved.getNom(), saved.getUsername(), saved.getEmail(), saved.getRole(),
                preferred != null ? preferred.getId() : null);
        return toResponse(saved);
    }

    /**
     * Met à jour un utilisateur existant.
     *
     * @param id identifiant du compte
     * @param request données mises à jour (mot de passe optionnel)
     * @return compte modifié
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si dernier admin retiré ;
     *         {@code 409} en cas de conflit identifiant/e-mail
     */
    public UserResponse update(Long id, UserRequest request) {
        User user = get(id);
        String username = request.getUsername().trim();
        String email = requireEmail(request.getEmail());
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà utilisé");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail déjà utilisé");
        }

        String newRole = normalizeRole(request.getRole());
        if (Roles.ADMIN.equals(user.getRole()) && !Roles.ADMIN.equals(newRole) && countAdmins() <= 1) {
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
        log.info("Utilisateur mis à jour: {} {} <{}> ({}) atelierPréféré={}",
                saved.getPrenom(), saved.getNom(), saved.getEmail(), saved.getUsername(),
                preferred != null ? preferred.getId() : null);
        return toResponse(saved);
    }

    /**
     * Supprime un utilisateur (interdit sur soi-même et sur le dernier administrateur).
     *
     * @param id identifiant du compte
     * @param currentUsername utilisateur connecté
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si auto-suppression ou dernier admin
     */
    public void delete(Long id, String currentUsername) {
        User user = get(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas supprimer votre propre compte");
        }
        if (Roles.ADMIN.equals(user.getRole()) && countAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de supprimer le dernier administrateur");
        }
        userRepository.delete(user);
    }

    private User requireActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Atelier resolvePreferredAtelier(User actor, String role, Long preferredAtelierId) {
        if (Roles.TECHNICIEN.equals(role)) {
            if (preferredAtelierId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Atelier préféré obligatoire pour un technicien");
            }
            return atelierService.requireAtelierForUserGroupe(actor, preferredAtelierId);
        }
        if (preferredAtelierId == null) {
            return null;
        }
        return atelierService.requireAtelierForUserGroupe(actor, preferredAtelierId);
    }

    private long countAdmins() {
        return userRepository.findAll().stream()
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle invalide (ADMIN ou TECHNICIEN)");
        }
        return normalized;
    }

    private User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
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

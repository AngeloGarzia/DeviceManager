package com.devicemanager.service;

import com.devicemanager.dto.UserRequest;
import com.devicemanager.dto.UserResponse;
import com.devicemanager.entity.User;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of(Roles.ADMIN, Roles.TECHNICIEN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(get(id));
    }

    public UserResponse create(UserRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà utilisé");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe obligatoire");
        }
        String role = normalizeRole(request.getRole());
        User saved = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .groupe(atelierService.requireCurrentAtelier().getCasino().getGroupe())
                .build());
        log.info("Utilisateur créé: {} ({})", saved.getUsername(), saved.getRole());
        return toResponse(saved);
    }

    public UserResponse update(Long id, UserRequest request) {
        User user = get(id);
        String username = request.getUsername().trim();
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà utilisé");
        }

        String newRole = normalizeRole(request.getRole());
        if (Roles.ADMIN.equals(user.getRole()) && !Roles.ADMIN.equals(newRole) && countAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de retirer le dernier administrateur");
        }

        user.setUsername(username);
        user.setRole(newRole);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return toResponse(userRepository.save(user));
    }

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

    private long countAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> Roles.ADMIN.equals(u.getRole()))
                .count();
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
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

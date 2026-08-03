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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AtelierService atelierService;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        List<AtelierSummary> ateliers = atelierService.listForUser(user.getUsername());
        Long atelierId = resolveLoginAtelierId(user, ateliers);

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        log.info("Connexion réussie pour utilisateur={} rôle={} atelier={}",
                user.getUsername(), user.getRole(), atelierId);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .username(user.getUsername())
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

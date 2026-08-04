package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Représentation d'un utilisateur renvoyée par l'API.
 */
@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String nom;
    private String prenom;
    private String email;
    private String role;
    private Long preferredAtelierId;
    private String preferredAtelierNom;
    private Instant createdAt;
}

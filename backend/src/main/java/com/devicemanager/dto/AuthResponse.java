package com.devicemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse d'authentification contenant le jeton JWT et le profil utilisateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String tokenType;
    /** Durée de validité du jeton en millisecondes. */
    private Long expiresInMs;
    private String username;
    private String nom;
    private String prenom;
    private String role;
    private Long groupeId;
    private String groupeNom;
    /** Atelier actif sélectionné à la connexion. */
    private Long atelierId;
    /** Liste des ateliers accessibles à l'utilisateur. */
    private List<AtelierSummary> ateliers;
    /** Si {@code true}, le client doit forcer un changement de mot de passe. */
    private Boolean mustChangePassword;
}

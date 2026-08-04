package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Compte utilisateur de l'application (technicien ou administrateur).
 * Rattaché à un groupe ; les techniciens sélectionnent un atelier de travail.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String nom = "";

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String prenom = "";

    @Column(nullable = false, unique = true, length = 160)
    @Builder.Default
    private String email = "";

    @Column(nullable = false)
    private String password;

    /** Rôle applicatif (ex. {@code ADMIN}, {@code TECHNICIEN}). */
    @Column(nullable = false, length = 30)
    private String role;

    /** Groupe d'appartenance — détermine les casinos et ateliers accessibles. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id", foreignKey = @ForeignKey(name = "fk_user_groupe"))
    private Groupe groupe;

    /** Dernier atelier sélectionné ; réutilisé à la prochaine connexion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_atelier_id", foreignKey = @ForeignKey(name = "fk_user_preferred_atelier"))
    private Atelier preferredAtelier;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

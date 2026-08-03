package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id", foreignKey = @ForeignKey(name = "fk_user_groupe"))
    private Groupe groupe;

    /** Dernier atelier sélectionné ; utilisé à la prochaine connexion. */
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

package com.devicemanager.entity;

import com.devicemanager.entity.coordonnees.Coordonnees;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Atelier de maintenance rattaché à un casino.
 * Unité de périmètre multi-tenant : pièces, MAS, SFM et commandes sont scopés par atelier.
 */
@Entity
@Table(name = "atelier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atelier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nom;

    /** Casino parent — détermine le groupe et la hiérarchie organisationnelle. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "casino_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_atelier_casino"))
    private Casino casino;

    /** Coordonnées postales, e-mails, téléphones et réseaux sociaux de l'atelier. */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "coordonnees_id", foreignKey = @ForeignKey(name = "fk_atelier_coordonnees"))
    private Coordonnees coordonnees;

    /** Utilisateurs responsables de l'atelier (relation N-N). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "atelier_responsable",
            joinColumns = @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_atelier_resp_atelier")),
            inverseJoinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_atelier_resp_user"))
    )
    @Builder.Default
    private Set<User> responsables = new HashSet<>();
}

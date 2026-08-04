package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Établissement de jeu appartenant à un groupe.
 * Regroupe un ou plusieurs ateliers de maintenance.
 */
@Entity
@Table(name = "casino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Casino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nom;

    /** Groupe propriétaire du casino. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_casino_groupe"))
    private Groupe groupe;

    /** Ateliers de maintenance rattachés à ce casino. */
    @OneToMany(mappedBy = "casino")
    @Builder.Default
    private List<Atelier> ateliers = new ArrayList<>();
}

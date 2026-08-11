package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bon d'intervention archivé : consommation de pièces détachées sur un atelier.
 */
@Entity
@Table(name = "intervention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro métier unique (ex. BI-2026-00042). */
    @Column(nullable = false, length = 40, unique = true)
    private String numero;

    @Column(name = "date_intervention", nullable = false)
    private LocalDateTime dateIntervention;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "technicien_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_intervention_technicien"))
    private User technicien;

    @Column(name = "technicien_nom", nullable = false, length = 120)
    private String technicienNom;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_intervention_atelier"))
    private Atelier atelier;

    /** Lieu / zone d'intervention (ex. salle machines). */
    @Column(length = 200)
    private String emplacement;

    /** Machine ou numéro MAS concerné. */
    @Column(name = "machine_mas", length = 120)
    private String machineMas;

    @Column(nullable = false, length = 500)
    private String motif;

    @Column(length = 2000)
    private String diagnostic;

    /** Travaux effectués (obligatoire pour un bon complet). */
    @Column(nullable = false, length = 2000)
    private String travaux;

    @Column(length = 2000)
    private String observations;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InterventionLigne> lignes = new ArrayList<>();

    public void addLigne(InterventionLigne ligne) {
        lignes.add(ligne);
        ligne.setIntervention(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (dateIntervention == null) {
            dateIntervention = LocalDateTime.now();
        }
    }
}

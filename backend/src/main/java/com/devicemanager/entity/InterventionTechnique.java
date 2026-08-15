package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Intervention technique libre réalisée sur une MAS.
 * Une visite multi-MAS produit une ligne par machine (même {@link #visiteGroupeId}).
 * Liens optionnels : FIT, commande de pièces, bon d'intervention.
 */
@Entity
@Table(name = "interventions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterventionTechnique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant partagé pour regrouper les lignes d'une même visite multi-MAS. */
    @Column(name = "visite_groupe_id", nullable = false, length = 36)
    private String visiteGroupeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_interventions_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_interventions_mas"))
    private Mas mas;

    @Column(name = "date_intervention", nullable = false)
    private LocalDateTime dateIntervention;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "technicien_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_interventions_technicien"))
    private User technicien;

    @Column(name = "technicien_nom", nullable = false, length = 120)
    private String technicienNom;

    @Column(length = 200)
    private String emplacement;

    @Column(nullable = false, length = 500)
    private String motif;

    @Column(length = 2000)
    private String diagnostic;

    @Column(nullable = false, length = 2000)
    private String travaux;

    @Column(length = 2000)
    private String observations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fit_id", foreignKey = @ForeignKey(name = "fk_interventions_fit"))
    private Fit fit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fit_ligne_id", foreignKey = @ForeignKey(name = "fk_interventions_fit_ligne"))
    private FitLigne fitLigne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", foreignKey = @ForeignKey(name = "fk_interventions_commande"))
    private Commande commande;

    /** Bon d'intervention (consommation de pièces), optionnel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_intervention_id",
            foreignKey = @ForeignKey(name = "fk_interventions_bon"))
    private Intervention bonIntervention;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

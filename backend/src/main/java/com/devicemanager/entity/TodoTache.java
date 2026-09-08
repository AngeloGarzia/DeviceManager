package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tâche « À faire » de l'atelier — créable par tout utilisateur authentifié.
 */
@Entity
@Table(name = "todo_tache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_tache_atelier"))
    private Atelier atelier;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private TodoTacheStatut statut = TodoTacheStatut.OPEN;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severite = "MEDIUM";

    @Column(name = "created_by_username", nullable = false, length = 120)
    private String createdByUsername;

    @Column(name = "created_by_display_name", length = 160)
    private String createdByDisplayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_username", length = 120)
    private String completedByUsername;

    @Column(name = "completed_by_display_name", length = 160)
    private String completedByDisplayName;

    /** Signature manuscrite (data URL PNG) obligatoire à la clôture. */
    @Lob
    @Column(name = "signature_cloture", columnDefinition = "LONGTEXT")
    private String signatureCloture;

    @Column(name = "signataire_cloture_nom", length = 120)
    private String signataireClotureNom;

    /** Commentaire libre saisi à la clôture. */
    @Column(name = "commentaire_cloture", length = 2000)
    private String commentaireCloture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", foreignKey = @ForeignKey(name = "fk_todo_tache_mas"))
    private Mas mas;

    /** Intervention technique libre (optionnelle). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_technique_id",
            foreignKey = @ForeignKey(name = "fk_todo_tache_it"))
    private InterventionTechnique interventionTechnique;

    /** Bon d'intervention pièces (optionnel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id",
            foreignKey = @ForeignKey(name = "fk_todo_tache_bi"))
    private Intervention intervention;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (statut == null) {
            statut = TodoTacheStatut.OPEN;
        }
        if (severite == null || severite.isBlank()) {
            severite = "MEDIUM";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

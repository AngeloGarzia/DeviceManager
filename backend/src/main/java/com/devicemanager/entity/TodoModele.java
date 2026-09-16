package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Modèle de tâche simple — permet une création rapide d'une {@link TodoTache} ponctuelle.
 */
@Entity
@Table(name = "todo_modele")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoModele {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_modele_atelier"))
    private Atelier atelier;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severite = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", foreignKey = @ForeignKey(name = "fk_todo_modele_mas"))
    private Mas mas;

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(name = "created_by_username", nullable = false, length = 120)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (severite == null || severite.isBlank()) {
            severite = "MEDIUM";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

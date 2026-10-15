package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ligne de checklist « nettoyage MAS » rattachée à une tâche {@link TodoTacheType#NETTOYAGE_MAS}.
 */
@Entity
@Table(name = "todo_nettoyage_mas_ligne", uniqueConstraints = @UniqueConstraint(
        name = "uk_todo_nettoyage_tache_mas",
        columnNames = {"todo_tache_id", "mas_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoNettoyageMasLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_tache_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_nettoyage_tache"))
    private TodoTache todoTache;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_nettoyage_mas"))
    private Mas mas;

    @Column(nullable = false)
    @Builder.Default
    private boolean coche = false;

    @Column(name = "coche_at")
    private LocalDateTime cocheAt;

    @Column(name = "coche_by_username", length = 120)
    private String cocheByUsername;

    @Column(name = "coche_by_display_name", length = 160)
    private String cocheByDisplayName;
}

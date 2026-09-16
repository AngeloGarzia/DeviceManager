package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Règle de génération de tâches « À faire » récurrentes.
 */
@Entity
@Table(name = "todo_recurrence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoRecurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_recurrence_atelier"))
    private Atelier atelier;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severite = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", foreignKey = @ForeignKey(name = "fk_todo_recurrence_mas"))
    private Mas mas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoRecurrenceFrequence frequence;

    @Column(name = "interval_days")
    private Integer intervalDays;

    /** 1 = lundi … 7 = dimanche (ISO). */
    @Column(name = "jour_semaine")
    private Integer jourSemaine;

    /** 1–28. */
    @Column(name = "jour_mois")
    private Integer jourMois;

    @Column(name = "heure_due", nullable = false)
    @Builder.Default
    private LocalTime heureDue = LocalTime.of(8, 0);

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "prochaine_echeance")
    private LocalDateTime prochaineEcheance;

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
        if (heureDue == null) {
            heureDue = LocalTime.of(8, 0);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

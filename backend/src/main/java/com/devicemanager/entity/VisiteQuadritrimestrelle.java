package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Visite quadritrimestrielle d'un SFM pour une marque de ses compétences.
 * Périodicité métier : tous les 4 mois (calculée sur la dernière {@link #dateVisite}).
 */
@Entity
@Table(name = "visite_quadritrimestrelle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisiteQuadritrimestrelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_visite_quadri_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sfm_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_visite_quadri_sfm"))
    private Sfm sfm;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "marque_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_visite_quadri_marque"))
    private MarqueMas marque;

    @Column(name = "date_visite", nullable = false)
    private LocalDate dateVisite;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

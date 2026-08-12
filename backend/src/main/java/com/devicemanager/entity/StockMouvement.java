package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mouvement de stock archivé (consommation, réception, édition manuelle).
 */
@Entity
@Table(name = "stock_mouvement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMouvement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_mouvement_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_mouvement_device"))
    private Device device;

    @Column(name = "piece_nom", nullable = false, length = 120)
    private String pieceNom;

    @Column(name = "piece_reference", length = 80)
    private String pieceReference;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "stock_avant", nullable = false)
    private Integer stockAvant;

    @Column(name = "stock_apres", nullable = false)
    private Integer stockApres;

    /** {@code INTERVENTION}, {@code ORDER_RECEIVE} ou {@code MANUAL}. */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "acteur_nom", length = 120)
    private String acteurNom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

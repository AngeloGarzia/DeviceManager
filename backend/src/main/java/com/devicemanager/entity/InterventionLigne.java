package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ligne d'un bon d'intervention : pièce consommée et quantités / stocks archivés.
 */
@Entity
@Table(name = "intervention_ligne")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterventionLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_intervention_ligne_intervention"))
    private Intervention intervention;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_intervention_ligne_device"))
    private Device device;

    /** Nom de la pièce au moment de la consommation (archive). */
    @Column(name = "piece_nom", nullable = false, length = 120)
    private String pieceNom;

    @Column(name = "piece_reference", length = 80)
    private String pieceReference;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "stock_avant", nullable = false)
    private Integer stockAvant;

    @Column(name = "stock_apres", nullable = false)
    private Integer stockApres;
}

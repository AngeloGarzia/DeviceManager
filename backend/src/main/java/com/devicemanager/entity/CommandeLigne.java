package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ligne d'une commande : une pièce ({@link Device}) et la quantité demandée.
 */
@Entity
@Table(name = "commande_ligne")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandeLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_commande_ligne_commande"))
    private Commande commande;

    /** Pièce commandée — nullable après purge (snapshot piece_nom conservé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id",
            foreignKey = @ForeignKey(name = "fk_commande_ligne_device"))
    private Device device;

    /** Nom archivé au moment de la commande / purge. */
    @Column(name = "piece_nom", length = 120)
    private String pieceNom;

    @Column(name = "piece_reference", length = 80)
    private String pieceReference;

    @Column(nullable = false)
    private Integer quantite;
}

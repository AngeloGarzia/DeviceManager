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

    /** Pièce commandée. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_commande_ligne_device"))
    private Device device;

    @Column(nullable = false)
    private Integer quantite;
}

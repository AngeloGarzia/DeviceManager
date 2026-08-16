package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Point de prix confirmé pour une pièce (historique immuable ; invalidation soft).
 */
@Entity
@Table(name = "device_prix_observation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePrixObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prix_obs_atelier"))
    private Atelier atelier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_prix_obs_device"))
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id",
            foreignKey = @ForeignKey(name = "fk_prix_obs_commande"))
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private PrixSource source = PrixSource.DEVIS;

    @Column(name = "unit_price_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceHt;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "quantity_on_quote")
    private Integer quantityOnQuote;

    @Column(name = "devis_designation", length = 255)
    private String devisDesignation;

    @Column(name = "devis_reference", length = 120)
    private String devisReference;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by", nullable = false, length = 80)
    private String confirmedBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean invalidated = false;

    @Column(name = "invalidated_reason", length = 255)
    private String invalidatedReason;
}

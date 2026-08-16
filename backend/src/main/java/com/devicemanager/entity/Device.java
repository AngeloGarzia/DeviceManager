package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pièce détachée ou consommable géré dans un atelier.
 * Peut être liée à un SFM (fournisseur) et/ou une MAS (référence catalogue).
 * Le nom et la référence sont uniques par atelier.
 */
@Entity
@Table(
        name = "device",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_nom_atelier", columnNames = {"nom", "atelier_id"}),
                @UniqueConstraint(name = "uk_device_reference_atelier", columnNames = {"reference", "atelier_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 80)
    private String reference;

    @Column(name = "usage_text", nullable = false, length = 500)
    private String usage;

    /**
     * Fiche technique détaillée (manuel / datasheet / notice, saisie ou extrait IA).
     */
    @Column(name = "information_technique", columnDefinition = "TEXT")
    private String informationTechnique;

    @Column(name = "date_acquisition", nullable = false)
    private LocalDate dateAcquisition;

    /** Indique si la pièce n'est plus utilisée en exploitation. */
    @Column(nullable = false)
    private boolean obsolete;

    /** Quantité disponible en stock (0 = rupture). */
    @Column(nullable = false)
    @Builder.Default
    private int stock = 0;

    /** Dernier prix unitaire HT confirmé (dénormalisé depuis l'historique). */
    @Column(name = "last_unit_price_ht", precision = 12, scale = 2)
    private java.math.BigDecimal lastUnitPriceHt;

    @Column(name = "last_unit_price_at")
    private java.time.LocalDateTime lastUnitPriceAt;

    /** Photo principale (1re) — synchronisée pour listes et commandes. */
    @Column(name = "photo_key", length = 512)
    private String photoKey;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<DevicePhoto> photos = new ArrayList<>();

    /** Documents (PDF ou image : manuel, datasheet, notice) — un par type. */
    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<DeviceDocument> documents = new ArrayList<>();

    /** Fournisseur SFM associé (optionnel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sfm_id")
    private Sfm sfm;

    /** Référence MAS du catalogue (optionnelle). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id")
    private Mas mas;

    /**
     * Marque de la pièce — même catalogue que la MAS ({@code marque_mas}).
     * Héritée de la MAS liée lorsqu'elle est renseignée.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "marque_id",
            foreignKey = @ForeignKey(name = "fk_device_marque"))
    private MarqueMas marque;

    /** Atelier propriétaire — périmètre multi-tenant. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_device_atelier"))
    private Atelier atelier;
}

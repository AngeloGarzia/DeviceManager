package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(name = "usage_text", nullable = false, length = 500)
    private String usage;

    @Column(name = "date_acquisition", nullable = false)
    private LocalDate dateAcquisition;

    @Column(nullable = false)
    private boolean obsolete;

    @Column(name = "photo_key", length = 512)
    private String photoKey;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sfm_id", nullable = false)
    private Sfm sfm;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mas_id", nullable = false)
    private Mas mas;

    /**
     * Marque de la pièce — même catalogue que la MAS ({@code marque_mas}).
     * Héritée de la MAS liée (peut donc être identique à {@code mas.marque}).
     */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "marque_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_device_marque"))
    private MarqueMas marque;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", foreignKey = @ForeignKey(name = "fk_device_atelier"))
    private Atelier atelier;
}

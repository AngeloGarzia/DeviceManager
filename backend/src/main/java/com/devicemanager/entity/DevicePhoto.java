package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Photo attachée à une pièce ({@link Device}).
 * L'ordre d'affichage est défini par {@link #position} ; la première photo alimente les champs dénormalisés du device.
 */
@Entity
@Table(name = "device_photo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevicePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_device_photo_device"))
    private Device device;

    /** Clé objet de stockage (S3 ou blob local). */
    @Column(name = "photo_key", nullable = false, length = 512)
    private String photoKey;

    @Column(name = "photo_url", nullable = false, length = 1024)
    private String photoUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** Position dans la galerie (0 = photo principale). */
    @Column(nullable = false)
    private int position;
}

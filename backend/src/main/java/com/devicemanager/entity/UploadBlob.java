package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Contenu binaire des uploads locaux.
 * Permet de conserver les fichiers malgré le disque éphémère de Render.
 */
@Entity
@Table(name = "upload_blob")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadBlob {

    /** Clé objet correspondant au chemin de stockage (identifiant primaire). */
    @Id
    @Column(name = "object_key", length = 512)
    private String objectKey;

    /** Chargement eager : les {@code @Lob} lazy sur {@code byte[]} sont peu fiables sans enhancement (prod Render). */
    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;
}

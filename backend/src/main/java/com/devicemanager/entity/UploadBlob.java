package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Contenu binaire des uploads locaux — survit aux redéploiements Render (disque éphémère).
 */
@Entity
@Table(name = "upload_blob")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadBlob {

    @Id
    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;
}

package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Document PDF attaché à une pièce ({@link Device}) : manuel, datasheet ou notice.
 * Un seul document par type et par pièce.
 */
@Entity
@Table(
        name = "device_document",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_document_type", columnNames = {"device_id", "doc_type"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_device_document_device"))
    private Device device;

    /** {@code MANUAL} | {@code DATASHEET} | {@code NOTICE}. */
    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @Column(name = "file_url", nullable = false, length = 1024)
    private String fileUrl;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;
}

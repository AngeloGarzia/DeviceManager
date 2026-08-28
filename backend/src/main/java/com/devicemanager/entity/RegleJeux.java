package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Règle de jeux du catalogue global, avec document PDF associé.
 * Peut exister sans MAS rattachée ; chaque MAS doit en référencer au moins une.
 */
@Entity
@Table(name = "regle_jeux")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegleJeux {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique (généré à partir du libellé). */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, unique = true, length = 200)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @Column(name = "file_url", nullable = false, length = 1024)
    private String fileUrl;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
}

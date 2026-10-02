package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Photo attachée à une {@link TodoTache} (capture caméra, 0 à 5 par tâche).
 */
@Entity
@Table(name = "todo_tache_photo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTachePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_tache_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_todo_tache_photo_todo"))
    private TodoTache todoTache;

    @Column(name = "photo_key", nullable = false, length = 512)
    private String photoKey;

    @Column(name = "photo_url", nullable = false, length = 1024)
    private String photoUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false)
    private int position;
}

package com.devicemanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mémoire synaptique IA d'un atelier : vue d'ensemble + faits récents persistés.
 */
@Entity
@Table(name = "atelier_memoire_synaptique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtelierMemoireSynaptique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atelier_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_atelier_memoire_synaptique_atelier"))
    private Atelier atelier;

    /** Résumé textuel de la situation atelier (injecté dans le prompt IA). */
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String overview;

    /** Anneau de faits JSON (événements récents). */
    @Lob
    @Column(name = "facts_json", columnDefinition = "MEDIUMTEXT")
    private String factsJson;

    @Column(name = "last_event_type", length = 80)
    private String lastEventType;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Représentation d'une règle de jeux renvoyée par l'API.
 */
@Data
@Builder
public class RegleJeuxResponse {
    private Long id;
    private String code;
    private String label;
    private String description;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    /** Alias pour les listes déroulantes. */
    private Long value;
    /** Nombre de MAS rattachées (0 = supprimable). */
    private long masCount;
    /** MAS rattachées (détail sur GET / regles-jeux/{id} et après mise à jour des liens). */
    private List<Long> masIds;
    private List<RegleJeuxMasSummary> masses;
}

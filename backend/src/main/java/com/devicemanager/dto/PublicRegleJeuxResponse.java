package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résumé public d'une règle de jeux (lecture seule, sans URLs internes).
 */
@Data
@Builder
public class PublicRegleJeuxResponse {
    private Long id;
    private String label;
    private String description;
    private String originalName;
    private String contentType;
    private Long fileSize;
}

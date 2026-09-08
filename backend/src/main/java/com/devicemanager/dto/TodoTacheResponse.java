package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Tâche « À faire » exposée par l'API.
 */
@Data
@Builder
public class TodoTacheResponse {

    private Long id;
    private String titre;
    private String description;
    private String statut;
    private String severite;
    private String createdByUsername;
    private String createdByDisplayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String completedByUsername;
    private String completedByDisplayName;
    private String signatureCloture;
    private String signataireClotureNom;
    private String commentaireCloture;

    /** Alias métier pour l'UI. */
    private LocalDateTime dateCreation;
    private String responsableCreation;
    private LocalDateTime dateCloture;
    private String responsableCloture;

    private Long masId;
    private String masNumero;

    private Long interventionTechniqueId;
    private String interventionTechniqueLabel;
    private Long interventionId;
    private String interventionNumero;

    /** Compatibilité liste unifiée (type fixe USER). */
    private String type;
    private String severity;
    private String title;
    private String link;
    private Long relatedId;
    private LocalDateTime since;
}

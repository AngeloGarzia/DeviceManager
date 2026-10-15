package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Création / mise à jour d'une tâche « À faire ».
 */
@Data
public class TodoTacheRequest {

    @NotBlank
    @Size(max = 200)
    private String titre;

    @Size(max = 2000)
    private String description;

    /** HIGH / MEDIUM / LOW — défaut MEDIUM. */
    private String severite;

    /** STANDARD (défaut) ou NETTOYAGE_MAS. */
    private String typeTache;

    private Long masId;

    /** MAS à nettoyer (obligatoire si typeTache = NETTOYAGE_MAS). */
    private List<Long> masIds;
}

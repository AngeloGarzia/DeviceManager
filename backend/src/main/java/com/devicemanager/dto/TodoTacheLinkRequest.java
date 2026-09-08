package com.devicemanager.dto;

import lombok.Data;

/**
 * Rattachement d'une intervention (technique et/ou bon pièces) à une tâche.
 */
@Data
public class TodoTacheLinkRequest {

    private Long interventionTechniqueId;
    private Long interventionId;
    /** Si true, efface le lien correspondant (technique ou bon). */
    private Boolean clearTechnique;
    private Boolean clearBon;
}

package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Changement de statut d'une tâche.
 * La clôture ({@code DONE}) exige une signature manuscrite.
 */
@Data
public class TodoTacheStatusRequest {

    @NotBlank
    private String statut;

    /** Data URL PNG — obligatoire si statut = DONE. */
    private String signatureCloture;

    /** Nom du signataire de clôture (défaut = responsable connecté). */
    private String signataireClotureNom;

    /** Commentaire libre à la clôture (optionnel). */
    private String commentaireCloture;

    /** Date/heure de clôture optionnelle (défaut = maintenant). */
    private java.time.LocalDateTime dateHeureCloture;
}

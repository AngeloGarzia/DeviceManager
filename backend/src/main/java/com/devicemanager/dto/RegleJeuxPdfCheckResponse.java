package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat du contrôle PDF d'une règle de jeux (présence, validité, lisibilité).
 */
@Data
@Builder
public class RegleJeuxPdfCheckResponse {
    private Long regleJeuxId;
    /** Fichier trouvé dans le stockage. */
    private boolean present;
    /** PDF structurellement valide (ouvrable). */
    private boolean valid;
    /** Texte extractible (pas seulement un scan image). */
    private boolean readable;
    private int pageCount;
    private String message;
}

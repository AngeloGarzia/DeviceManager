package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Représentation d'une référence MAS renvoyée par l'API.
 */
@Data
@Builder
public class MasResponse {
    private Long id;
    private String numero;
    private Long marqueId;
    /** Code court de la marque. */
    private String marque;
    /** Libellé complet de la marque. */
    private String marqueLabel;
    private boolean utilise;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ligne extraite du devis sans correspondance claire avec une pièce de la commande.
 */
@Data
@Builder
public class AiDevisUnmatchedPart {
    private String designation;
    private String reference;
}

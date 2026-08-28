package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * MAS rattachée à une règle de jeux (aperçu pour l'UI).
 */
@Data
@Builder
public class RegleJeuxMasSummary {
    private Long id;
    private String numero;
    private String marqueLabel;
}

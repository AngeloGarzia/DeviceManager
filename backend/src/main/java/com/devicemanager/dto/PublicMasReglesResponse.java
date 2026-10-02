package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Vue publique d'une MAS pour consultation des règles de jeux via token QR.
 */
@Data
@Builder
public class PublicMasReglesResponse {
    private String masNumero;
    private String marqueLabel;
    private List<PublicRegleJeuxResponse> regles;
}

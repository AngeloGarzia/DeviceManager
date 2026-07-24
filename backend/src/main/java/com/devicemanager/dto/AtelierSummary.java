package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AtelierSummary {
    private Long id;
    private String nom;
    private Long casinoId;
    private String casinoNom;
    private Long groupeId;
    private String groupeNom;
    private String label;
}

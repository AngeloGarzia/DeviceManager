package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CasinoSummary {
    private Long id;
    private String nom;
    private Long groupeId;
    private String groupeNom;
}

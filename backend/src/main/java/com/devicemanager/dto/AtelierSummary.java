package com.devicemanager.dto;

import com.devicemanager.dto.coordonnees.CoordonneesDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    private CoordonneesDto coordonnees;
    @Builder.Default
    private List<AtelierResponsableDto> responsables = new ArrayList<>();
}

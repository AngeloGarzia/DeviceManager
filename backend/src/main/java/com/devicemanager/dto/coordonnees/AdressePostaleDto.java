package com.devicemanager.dto.coordonnees;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdressePostaleDto {
    private String ligne1;
    private String ligne2;
    private String codePostal;
    private String ville;
    private String pays;
}

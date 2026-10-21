package com.devicemanager.dto.coordonnees;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Adresse postale transférée entre l'API et l'entité {@link com.devicemanager.entity.coordonnees.AdressePostale}.
 * Explicit constructors required alongside {@code @Builder} for Jackson request deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdressePostaleDto {
    private String ligne1;
    private String ligne2;
    private String codePostal;
    private String ville;
    private String pays;
}

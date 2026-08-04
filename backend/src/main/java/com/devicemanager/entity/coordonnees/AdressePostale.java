package com.devicemanager.entity.coordonnees;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdressePostale {

    @Column(name = "adresse_ligne1", length = 160)
    private String ligne1;

    @Column(name = "adresse_ligne2", length = 160)
    private String ligne2;

    @Column(name = "adresse_code_postal", length = 20)
    private String codePostal;

    @Column(name = "adresse_ville", length = 120)
    private String ville;

    @Column(name = "adresse_pays", length = 80)
    private String pays;
}

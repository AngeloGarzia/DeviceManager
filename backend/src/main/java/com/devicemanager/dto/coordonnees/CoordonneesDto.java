package com.devicemanager.dto.coordonnees;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CoordonneesDto {
    private Long id;

    @Valid
    private AdressePostaleDto adresse;

    @Valid
    @Builder.Default
    private List<EmailCoordDto> emails = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<TelephoneCoordDto> telephones = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<ReseauSocialDto> reseauxSociaux = new ArrayList<>();
}

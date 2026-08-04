package com.devicemanager.dto.coordonnees;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensemble de coordonnées d'un atelier exposé par l'API.
 */
@Data
@Builder
public class CoordonneesDto {
    /** Identifiant du bloc coordonnées (absent à la création). */
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

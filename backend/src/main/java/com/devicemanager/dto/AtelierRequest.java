package com.devicemanager.dto;

import com.devicemanager.dto.coordonnees.AdressePostaleDto;
import com.devicemanager.dto.coordonnees.EmailCoordDto;
import com.devicemanager.dto.coordonnees.ReseauSocialDto;
import com.devicemanager.dto.coordonnees.TelephoneCoordDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Requête de création ou mise à jour d'un atelier.
 */
@Data
public class AtelierRequest {

    @NotBlank
    @Size(max = 160)
    private String nom;

    /** Identifiant du casino parent. */
    @NotNull
    private Long casinoId;

    @Valid
    private AdressePostaleDto adresse;

    @Valid
    private List<EmailCoordDto> emails = new ArrayList<>();

    @Valid
    private List<TelephoneCoordDto> telephones = new ArrayList<>();

    @Valid
    private List<ReseauSocialDto> reseauxSociaux = new ArrayList<>();

    /** Identifiants des utilisateurs responsables de l'atelier. */
    private List<Long> responsableIds = new ArrayList<>();
}

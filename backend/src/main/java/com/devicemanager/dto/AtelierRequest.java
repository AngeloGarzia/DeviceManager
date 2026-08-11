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

    @NotBlank(message = "Le nom de l'atelier est obligatoire")
    @Size(max = 160, message = "Le nom de l'atelier ne doit pas dépasser 160 caractères")
    private String nom;

    /** Identifiant du casino parent. */
    @NotNull(message = "Le casino est obligatoire")
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

    /** Identifiants des utilisateurs pour lesquels cet atelier est l'atelier préféré. */
    private List<Long> utilisateurPrefereIds = new ArrayList<>();
}

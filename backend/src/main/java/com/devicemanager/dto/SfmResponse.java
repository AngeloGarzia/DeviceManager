package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Représentation complète d'un SFM renvoyée par l'API.
 */
@Data
@Builder
public class SfmResponse {
    private Long id;
    private String nom;
    /** Champs dénormalisés du premier contact (compatibilité / recherche). */
    private String responsable;
    private String telephone;
    private String email;
    private List<SfmContactResponse> contacts;
    private List<Long> marqueIds;
    private List<MarqueMasResponse> marques;
}

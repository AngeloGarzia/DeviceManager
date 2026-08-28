package com.devicemanager.dto;

import lombok.Data;

import java.util.List;

/**
 * Requête de rattachement de MAS à une règle de jeux (côté catalogue).
 */
@Data
public class RegleJeuxMasLinkRequest {
    /** Identifiants des MAS de l'atelier courant à rattacher à la règle. */
    private List<Long> masIds;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résumé d'un responsable d'atelier pour l'affichage dans les listes.
 */
@Data
@Builder
public class AtelierResponsableDto {
    private Long id;
    private String username;
    private String nom;
    private String prenom;
    private String email;
}

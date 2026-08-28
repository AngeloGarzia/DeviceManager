package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ArretMaintenanceResponse {

    private Long id;
    private Long masId;
    private String masNumero;
    private String masMarque;
    private String adminUsername;
    private String adminDisplayName;
    private LocalDateTime dateHeureArret;
    private LocalDateTime dateHeureReprise;
    private String motifArret;
    private boolean registreTechniqueAJour;
    private boolean actif;
    private LocalDateTime createdAt;
}

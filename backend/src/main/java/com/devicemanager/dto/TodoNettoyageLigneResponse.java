package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Ligne de checklist nettoyage MAS.
 */
@Data
@Builder
public class TodoNettoyageLigneResponse {

    private Long masId;
    private String masNumero;
    private boolean coche;
    private LocalDateTime cocheAt;
    private String cocheByUsername;
    private String cocheByDisplayName;
}

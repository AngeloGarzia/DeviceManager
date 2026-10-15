package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Coche / décoche une MAS dans une tâche de nettoyage.
 */
@Data
public class TodoNettoyageCocheRequest {

    @NotNull
    private Boolean coche;
}

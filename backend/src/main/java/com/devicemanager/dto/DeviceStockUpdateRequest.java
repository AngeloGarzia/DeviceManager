package com.devicemanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mise à jour rapide de la quantité en stock d'une pièce.
 */
@Data
public class DeviceStockUpdateRequest {

    @NotNull
    @Min(0)
    private Integer stock;
}

package com.devicemanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mise à jour rapide de la quantité en stock d'une pièce.
 */
@Data
public class DeviceStockUpdateRequest {

    @NotNull(message = "La quantité en stock est obligatoire")
    @Min(value = 0, message = "Le stock ne peut pas être négatif")
    private Integer stock;
}

package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Requête de soumission d'une commande de pièces par un technicien.
 */
@Data
public class OrderRequestDto {

    @NotEmpty(message = "Ajoutez au moins une pièce à la demande")
    @Valid
    private List<OrderRequestLineDto> lignes;

    @NotBlank(message = "Le message de la demande est obligatoire")
    @Size(max = 1000, message = "Le message ne doit pas dépasser 1000 caractères")
    private String message;

    /**
     * Ligne de commande : pièce et quantité demandée.
     */
    @Data
    public static class OrderRequestLineDto {
        /** Identifiant de la pièce commandée. */
        @NotNull(message = "La pièce est obligatoire sur chaque ligne")
        private Long deviceId;

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être au moins 1")
        private Integer quantite;
    }
}

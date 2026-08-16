package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Application sélective des mises à jour proposées par l'IA après analyse d'un devis.
 */
@Data
public class AiDevisApplyRequest {

    @NotEmpty(message = "Sélectionnez au moins une mise à jour")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "deviceId obligatoire")
        private Long deviceId;

        /** Appliquer le nom proposé. */
        private Boolean updateNom;

        /** Appliquer la référence proposée. */
        private Boolean updateReference;

        @Size(max = 120)
        private String suggestedNom;

        @Size(max = 80)
        private String suggestedReference;
    }
}

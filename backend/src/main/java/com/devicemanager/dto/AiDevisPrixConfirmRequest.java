package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Confirmation admin des prix extraits d'un devis.
 */
@Data
public class AiDevisPrixConfirmRequest {

    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long deviceId;
        @NotNull
        @PositiveOrZero
        private BigDecimal unitPriceHt;
        private Integer quantityOnQuote;
        private String devisDesignation;
        private String devisReference;
    }
}

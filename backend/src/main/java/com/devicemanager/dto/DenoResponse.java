package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Dénomination du référentiel MAS.
 */
@Data
@Builder
public class DenoResponse {
    private Long id;
    private BigDecimal valeur;
    private String label;
    /** Alias de {@link #id} pour compatibilité front (mat-select). */
    private Long value;
}

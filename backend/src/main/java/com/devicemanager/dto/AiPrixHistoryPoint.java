package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AiPrixHistoryPoint {
    private BigDecimal unitPriceHt;
    private String observedAt;
    private Long commandeId;
}

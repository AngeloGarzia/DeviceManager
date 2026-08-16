package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AiPrixDeviceContext {
    private Long deviceId;
    private String nom;
    private String reference;
    private BigDecimal newUnitPriceHt;
    private List<AiPrixHistoryPoint> history;
}

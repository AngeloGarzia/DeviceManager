package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class DevicePrixObservationResponse {
    private Long id;
    private Long deviceId;
    private String deviceNom;
    private Long commandeId;
    private String source;
    private BigDecimal unitPriceHt;
    private String currency;
    private Integer quantityOnQuote;
    private String devisDesignation;
    private String devisReference;
    private LocalDateTime observedAt;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private boolean invalidated;
}

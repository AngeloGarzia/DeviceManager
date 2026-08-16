package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DevicePrixAlerteResponse {
    private Long id;
    private Long deviceId;
    private String deviceNom;
    private String deviceReference;
    private Long observationId;
    private BigDecimal unitPriceHt;
    private String severity;
    private List<String> signals;
    private String aiSummary;
    private String status;
    private LocalDateTime createdAt;
    private String ackBy;
    private LocalDateTime ackAt;
}

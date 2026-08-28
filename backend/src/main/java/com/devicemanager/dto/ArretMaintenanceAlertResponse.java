package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ArretMaintenanceAlertResponse {

    private long count;
    private List<ArretMaintenanceAlertItem> items;

    @Data
    @Builder
    public static class ArretMaintenanceAlertItem {
        private Long id;
        private Long masId;
        private String masNumero;
        private LocalDateTime dateHeureArret;
    }
}

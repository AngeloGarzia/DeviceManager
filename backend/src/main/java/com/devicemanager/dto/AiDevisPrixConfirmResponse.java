package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Résultat de la confirmation des prix devis.
 */
@Data
@Builder
public class AiDevisPrixConfirmResponse {
    private OrderRequestResponse order;
    private int confirmedCount;
    private List<DevicePrixObservationResponse> observations;
    private List<DevicePrixAlerteResponse> alertes;
    private List<String> errors;
}

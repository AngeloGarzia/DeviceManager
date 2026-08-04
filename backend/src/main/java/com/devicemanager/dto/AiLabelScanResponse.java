package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiLabelScanResponse {
    private boolean enabled;
    private String nom;
    private String reference;
    private String marque;
    private String usage;
    private String rawText;
    private String notes;
}

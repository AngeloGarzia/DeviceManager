package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasResponse {
    private Long id;
    private String numero;
    private Long marqueId;
    private String marque;
    private String marqueLabel;
    private boolean utilise;
}

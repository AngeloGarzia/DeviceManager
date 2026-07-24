package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarqueMasResponse {
    private Long id;
    private String code;
    private String label;
    /** Compat front (anciennement value = enum). */
    private Long value;
}

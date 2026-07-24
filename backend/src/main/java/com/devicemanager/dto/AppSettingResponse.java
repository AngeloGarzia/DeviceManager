package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppSettingResponse {
    private String key;
    private String value;
    private String label;
    private String category;
    private boolean secret;
}

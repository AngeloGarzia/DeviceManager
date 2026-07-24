package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AppSettingsUpdateRequest {

    @NotNull
    private Map<String, String> values;
}

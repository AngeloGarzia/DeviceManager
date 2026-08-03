package com.devicemanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreferredAtelierRequest {

    @NotNull
    private Long atelierId;
}

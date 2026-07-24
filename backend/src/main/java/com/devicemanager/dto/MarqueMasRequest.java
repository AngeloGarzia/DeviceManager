package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarqueMasRequest {

    @NotBlank
    @Size(max = 120)
    private String label;
}

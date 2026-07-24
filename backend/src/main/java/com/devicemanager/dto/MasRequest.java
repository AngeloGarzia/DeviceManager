package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MasRequest {

    @NotBlank
    @Size(max = 80)
    private String numero;

    @NotNull
    private Long marqueId;

    @NotNull
    private Boolean utilise;
}

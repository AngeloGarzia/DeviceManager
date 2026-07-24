package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DeviceRequest {

    @NotBlank
    @Size(max = 120)
    private String nom;

    @NotBlank
    @Size(max = 80)
    private String reference;

    @NotBlank
    @Size(max = 500)
    private String usage;

    @NotNull
    private LocalDate dateAcquisition;

    @NotNull
    private Boolean obsolete;

    @NotNull
    private Long sfmId;

    @NotNull
    private Long masId;
}

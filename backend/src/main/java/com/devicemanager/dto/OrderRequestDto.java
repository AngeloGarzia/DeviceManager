package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequestDto {

    @NotEmpty
    @Valid
    private List<OrderRequestLineDto> lignes;

    @NotBlank
    @Size(max = 1000)
    private String message;

    @Data
    public static class OrderRequestLineDto {
        @NotNull
        private Long deviceId;

        @NotNull
        @Min(1)
        private Integer quantite;
    }
}

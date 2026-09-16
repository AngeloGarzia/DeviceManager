package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TodoModeleRequest {

    @NotBlank
    @Size(max = 200)
    private String titre;

    @Size(max = 2000)
    private String description;

    private String severite;

    private Long masId;

    private Integer position;
}

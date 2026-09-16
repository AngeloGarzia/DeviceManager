package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TodoModeleResponse {

    private Long id;
    private String titre;
    private String description;
    private String severite;
    private Long masId;
    private String masNumero;
    private int position;
    private String createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

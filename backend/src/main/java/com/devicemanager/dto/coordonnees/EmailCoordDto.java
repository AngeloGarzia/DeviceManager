package com.devicemanager.dto.coordonnees;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailCoordDto {
    private Long id;

    @Size(max = 160)
    private String valeur;

    private boolean principal;
}

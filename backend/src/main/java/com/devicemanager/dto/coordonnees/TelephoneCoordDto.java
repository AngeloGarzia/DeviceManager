package com.devicemanager.dto.coordonnees;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TelephoneCoordDto {
    private Long id;

    @Size(max = 40)
    private String valeur;

    @Size(max = 40)
    private String label;

    private boolean principal;
}

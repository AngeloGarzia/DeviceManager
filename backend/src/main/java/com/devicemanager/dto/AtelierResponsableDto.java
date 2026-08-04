package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AtelierResponsableDto {
    private Long id;
    private String username;
    private String nom;
    private String prenom;
    private String email;
}

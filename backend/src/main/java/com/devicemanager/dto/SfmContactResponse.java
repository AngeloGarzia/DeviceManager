package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SfmContactResponse {
    private Long id;
    private String nom;
    private String telephone;
    private String email;
}

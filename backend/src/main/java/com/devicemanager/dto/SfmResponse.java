package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SfmResponse {
    private Long id;
    private String nom;
    private String responsable;
    private String telephone;
    private String email;
    private List<SfmContactResponse> contacts;
    private List<Long> marqueIds;
    private List<MarqueMasResponse> marques;
}

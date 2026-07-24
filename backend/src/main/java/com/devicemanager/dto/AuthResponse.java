package com.devicemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String tokenType;
    private Long expiresInMs;
    private String username;
    private String role;
    private Long groupeId;
    private String groupeNom;
    private Long atelierId;
    private List<AtelierSummary> ateliers;
}

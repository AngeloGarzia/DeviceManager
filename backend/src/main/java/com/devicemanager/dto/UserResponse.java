package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String nom;
    private String prenom;
    private String email;
    private String role;
    private Instant createdAt;
}

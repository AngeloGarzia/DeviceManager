package com.devicemanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @NotBlank
    @Size(max = 80)
    private String nom;

    @NotBlank
    @Size(max = 80)
    private String prenom;

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(max = 30)
    private String role;
}

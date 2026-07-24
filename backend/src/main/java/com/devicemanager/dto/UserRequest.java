package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Size(max = 30)
    private String role;
}

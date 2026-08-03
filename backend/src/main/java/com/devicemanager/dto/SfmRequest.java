package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SfmRequest {

    @NotBlank
    @Size(max = 120)
    private String nom;

    @NotEmpty
    @Valid
    private List<SfmContactRequest> contacts;

    @NotEmpty(message = "Sélectionnez au moins une marque")
    private List<@NotNull Long> marqueIds;

    @Data
    public static class SfmContactRequest {
        private Long id;

        @NotBlank
        @Size(max = 120)
        private String nom;

        @NotBlank
        @Size(max = 40)
        private String telephone;

        @NotBlank
        @Email
        @Size(max = 160)
        private String email;

        /** Défaut true si absent (compat clients anciens). */
        private Boolean receiveOrderMails;
    }
}

package com.devicemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Requête de création ou mise à jour d'un SFM (fournisseur).
 */
@Data
public class SfmRequest {

    @NotBlank
    @Size(max = 120)
    private String nom;

    @NotEmpty
    @Valid
    private List<SfmContactRequest> contacts;

    /** Identifiants des marques couvertes par ce SFM. */
    @NotEmpty(message = "Sélectionnez au moins une marque")
    private List<@NotNull Long> marqueIds;

    /**
     * Contact rattaché à un SFM dans une requête de création ou mise à jour.
     */
    @Data
    public static class SfmContactRequest {
        /** Identifiant du contact existant (mise à jour) ; absent à la création. */
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

        /** Défaut {@code true} si absent (compatibilité clients anciens). */
        private Boolean receiveOrderMails;
    }
}

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

    @NotBlank(message = "Le nom du SFM est obligatoire")
    @Size(max = 120, message = "Le nom du SFM ne doit pas dépasser 120 caractères")
    private String nom;

    @NotEmpty(message = "Ajoutez au moins un contact SFM")
    @Valid
    private List<SfmContactRequest> contacts;

    /** Identifiants des marques couvertes par ce SFM. */
    @NotEmpty(message = "Sélectionnez au moins une marque")
    private List<@NotNull(message = "Marque invalide") Long> marqueIds;

    /**
     * Contact rattaché à un SFM dans une requête de création ou mise à jour.
     */
    @Data
    public static class SfmContactRequest {
        /** Identifiant du contact existant (mise à jour) ; absent à la création. */
        private Long id;

        @NotBlank(message = "Le nom du contact est obligatoire")
        @Size(max = 120, message = "Le nom du contact ne doit pas dépasser 120 caractères")
        private String nom;

        @NotBlank(message = "Le téléphone du contact est obligatoire")
        @Size(max = 40, message = "Le téléphone ne doit pas dépasser 40 caractères")
        private String telephone;

        @NotBlank(message = "L'e-mail du contact est obligatoire")
        @Email(message = "L'e-mail du contact est invalide")
        @Size(max = 160, message = "L'e-mail du contact ne doit pas dépasser 160 caractères")
        private String email;

        /** Défaut {@code true} si absent (compatibilité clients anciens). */
        private Boolean receiveOrderMails;

        /** Technicien SFM — partageable entre plusieurs SFM. */
        private Boolean technicienSfm;
    }
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat d'un envoi d'e-mail de test depuis l'administration.
 */
@Data
@Builder
public class MailTestResponse {
    private boolean success;
    private String message;
    /** Adresse du destinataire du test. */
    private String to;
}

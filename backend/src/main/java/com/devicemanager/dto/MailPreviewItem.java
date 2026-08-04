package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Aperçu d'un e-mail généré (test ou prévisualisation de commande).
 */
@Data
@Builder
public class MailPreviewItem {
    /** Type de destinataire : {@code ADMIN} ou {@code SFM}. */
    private String kind;
    private String to;
    private String subject;
    private String body;
    /** Nom du SFM concerné (si applicable). */
    private String sfmNom;
}

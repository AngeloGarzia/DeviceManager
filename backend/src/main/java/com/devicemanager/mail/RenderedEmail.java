package com.devicemanager.mail;

import com.devicemanager.mail.templates.EmailHtml;

/**
 * E-mail rendu (sujet + versions texte et HTML).
 * Le nota responsive est ajouté automatiquement au corps texte ;
 * la version HTML le reçoit via {@link EmailHtml#emailShell}.
 */
public record RenderedEmail(String subject, String text, String html) {

    public RenderedEmail {
        text = EmailHtml.appendResponsiveNotaText(text);
    }
}

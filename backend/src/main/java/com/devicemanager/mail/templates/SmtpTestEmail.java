package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

/**
 * E-mail de test SMTP (Setup / Brevo).
 */
public final class SmtpTestEmail {

    private SmtpTestEmail() {
    }

    public static RenderedEmail render() {
        String subject = "DeviceManager — test SMTP";
        String text = """
                Bonjour,

                Ceci est un e-mail de test envoyé depuis DeviceManager.
                Si vous le recevez, la messagerie est correctement configurée.

                — DeviceManager
                """;
        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour,</p>
                <p style="margin:0 0 12px;">Ceci est un e-mail de test envoyé depuis DeviceManager.</p>
                <p style="margin:0 0 12px;">Si vous le recevez, la messagerie (ex. Brevo) est correctement configurée.</p>
                """;
        String html = EmailHtml.emailShell("Test SMTP", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }
}

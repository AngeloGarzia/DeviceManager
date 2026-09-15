package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

/**
 * E-mail de réinitialisation de mot de passe (lien à usage unique).
 */
public final class PasswordResetEmail {

    private PasswordResetEmail() {
    }

    public record Context(String prenom, String resetUrl) {
    }

    public static RenderedEmail render(Context ctx) {
        String name = blankOr(ctx.prenom(), "");
        String greeting = name.isBlank() ? "Bonjour," : "Bonjour " + name + ",";
        String url = blankOr(ctx.resetUrl(), "");

        String subject = "DeviceManager — réinitialisation du mot de passe";
        String text = """
                %s

                Vous avez demandé la réinitialisation de votre mot de passe DeviceManager.
                Ce lien est valable 24 heures et ne peut être utilisé qu'une seule fois :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.

                — DeviceManager
                """.formatted(greeting, url);

        String bodyHtml = """
                <p style="margin:0 0 12px;">%s</p>
                <p style="margin:0 0 12px;">Vous avez demandé la réinitialisation de votre mot de passe DeviceManager.
                Ce lien est valable <strong>24 heures</strong> et ne peut être utilisé qu'une seule fois.</p>
                <p style="margin:16px 0 0;font-size:13px;color:#6b7280;">Si vous n'êtes pas à l'origine de cette demande,
                ignorez cet e-mail.</p>
                """.formatted(EmailHtml.escapeHtml(greeting));

        String html = EmailHtml.emailShell(
                "Réinitialisation du mot de passe",
                bodyHtml,
                "Réinitialiser",
                url);
        return new RenderedEmail(subject, text, html);
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

/**
 * E-mail de bienvenue / accès compte (identifiant + mot de passe temporaire + lien reset).
 */
public final class PasswordWelcomeEmail {

    private PasswordWelcomeEmail() {
    }

    public record Context(
            String prenom,
            String username,
            String temporaryPassword,
            String appUrl,
            String resetUrl) {
    }

    public static RenderedEmail render(Context ctx) {
        String name = blankOr(ctx.prenom(), "");
        String greeting = name.isBlank() ? "Bonjour," : "Bonjour " + name + ",";
        String username = blankOr(ctx.username(), "");
        String tempPassword = blankOr(ctx.temporaryPassword(), "");
        String appUrl = blankOr(ctx.appUrl(), "");
        String url = blankOr(ctx.resetUrl(), "");

        String subject = "DeviceManager — bienvenue et accès à votre compte";
        String text = """
                %s

                Voici vos informations de connexion :
                Identifiant : %s
                Mot de passe temporaire : %s

                Lien de l'application : %s

                À la première connexion, vous devrez changer ce mot de passe.
                Vous pouvez aussi le réinitialiser dès maintenant via ce lien
                (valable 24 heures, usage unique) :

                %s

                — DeviceManager
                """.formatted(greeting, username, tempPassword, appUrl, url);

        String bodyHtml = """
                <p style="margin:0 0 12px;">%s</p>
                <p style="margin:0 0 6px;font-weight:600;">Vos informations de connexion</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr>
                    <td style="padding:2px 12px 2px 0;color:#6b7280;">Identifiant</td>
                    <td><strong>%s</strong></td>
                  </tr>
                  <tr>
                    <td style="padding:2px 12px 2px 0;color:#6b7280;">Mot de passe temporaire</td>
                    <td><strong>%s</strong></td>
                  </tr>
                  <tr>
                    <td style="padding:2px 12px 2px 0;color:#6b7280;">Application</td>
                    <td><a href="%s" style="color:#2563eb;text-decoration:underline;">%s</a></td>
                  </tr>
                </table>
                <p style="margin:0 0 12px;">À la première connexion, vous devrez changer ce mot de passe.
                Vous pouvez aussi le réinitialiser dès maintenant via le bouton ci-dessous
                (lien valable <strong>24 heures</strong>, usage unique).</p>
                """.formatted(
                EmailHtml.escapeHtml(greeting),
                EmailHtml.escapeHtml(username),
                EmailHtml.escapeHtml(tempPassword),
                EmailHtml.escapeHtml(appUrl),
                EmailHtml.escapeHtml(appUrl));

        String html = EmailHtml.emailShell(
                "Accès à votre compte",
                bodyHtml,
                "Réinitialiser mon mot de passe",
                url);
        return new RenderedEmail(subject, text, html);
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

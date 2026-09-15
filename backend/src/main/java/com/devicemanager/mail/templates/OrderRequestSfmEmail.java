package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.util.List;

/**
 * Demande de devis envoyée aux SFM à la validation admin.
 */
public final class OrderRequestSfmEmail {

    private OrderRequestSfmEmail() {
    }

    public record LineItem(String name, String reference, int quantity) {
    }

    public record Context(
            Long orderId,
            List<LineItem> lines,
            String adminName,
            String adminEmail,
            String requesterEmail) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        String subject = ctx.orderId() == null
                ? "Demande de devis #(n° à l'envoi)"
                : "Demande de devis #" + ctx.orderId();

        List<String> lineTexts = ctx.lines() == null ? List.of() : ctx.lines().stream()
                .map(OrderRequestSfmEmail::formatLineText)
                .toList();
        String linesBlock = lineTexts.isEmpty()
                ? "(aucune pièce)"
                : String.join("\n", lineTexts.stream().map(l -> "- " + l).toList());

        String adminName = blankOr(ctx.adminName(), "(Prénom Nom de l'administrateur)");
        String adminEmail = blankOr(ctx.adminEmail(), "(e-mail de l'administrateur)");
        String requesterEmail = blankOr(ctx.requesterEmail(), "(e-mail du demandeur)");

        String text = """
                Bonjour,

                Pouvez-vous nous faire un devis pour les pièces détachées suivantes :

                %s

                Merci, bien à vous.
                %s
                %s
                %s
                """.formatted(linesBlock, adminName, adminEmail, requesterEmail);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour,</p>
                <p style="margin:0 0 12px;">Pouvez-vous nous faire un devis pour les pièces détachées suivantes :</p>
                %s
                <p style="margin:16px 0 0;">Merci, bien à vous.</p>
                <p style="margin:8px 0 0;">%s<br/>%s<br/>%s</p>
                """.formatted(
                EmailHtml.bulletList(lineTexts),
                EmailHtml.escapeHtml(adminName),
                EmailHtml.escapeHtml(adminEmail),
                EmailHtml.escapeHtml(requesterEmail));

        String html = EmailHtml.emailShell("Demande de devis", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(LineItem line) {
        StringBuilder sb = new StringBuilder(line.name());
        if (line.reference() != null && !line.reference().isBlank()) {
            sb.append(" (réf. ").append(line.reference()).append(")");
        }
        sb.append(" × ").append(line.quantity());
        return sb.toString();
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

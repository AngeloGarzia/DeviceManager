package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Relance admin — commandes PENDING/SENT trop anciennes.
 */
public final class OrderRequestRelanceEmail {

    private OrderRequestRelanceEmail() {
    }

    public record OrderLine(
            Long orderId,
            String atelierName,
            String technicienName,
            String status,
            String dateDemande,
            long ageDays,
            int lineCount) {
    }

    public record Context(String periodKey, int minAgeDays, List<OrderLine> lines) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        int count = ctx.lines().size();
        String subject = "Commandes en attente — " + count + " demande(s) ≥ " + ctx.minAgeDays() + " j";

        List<String> textLines = new ArrayList<>();
        for (OrderLine line : ctx.lines()) {
            textLines.add(formatLineText(line));
        }
        String linesBlock = textLines.isEmpty()
                ? "(aucune)"
                : String.join("\n", textLines.stream().map(l -> "- " + l).toList());

        String text = """
                Bonjour Administrateur,

                DeviceManager signale des demandes de commande encore en attente de validation
                depuis au moins %d jour(s).

                Période : %s
                Demandes concernées : %d

                Détail :
                %s

                Connectez-vous à DeviceManager pour valider ou traiter ces demandes.

                — DeviceManager
                """.formatted(ctx.minAgeDays(), ctx.periodKey(), count, linesBlock);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour Administrateur,</p>
                <p style="margin:0 0 12px;">DeviceManager signale des demandes de commande encore en attente
                de validation depuis au moins <strong>%d</strong> jour(s).</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Période</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Demandes</td><td><strong>%d</strong></td></tr>
                </table>
                <p style="margin:0 0 6px;font-weight:600;">Détail</p>
                %s
                <p style="margin:16px 0 0;">Connectez-vous à DeviceManager pour valider ou traiter ces demandes.</p>
                """.formatted(
                ctx.minAgeDays(),
                EmailHtml.escapeHtml(ctx.periodKey()),
                count,
                EmailHtml.bulletList(textLines));

        String html = EmailHtml.emailShell("Commandes en attente", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(OrderLine line) {
        return "#%s — %s — %s — %s depuis %s (%d j, %d ligne(s))".formatted(
                line.orderId() == null ? "?" : line.orderId(),
                nullToDash(line.atelierName()),
                nullToDash(line.technicienName()),
                nullToDash(line.status()),
                nullToDash(line.dateDemande()),
                line.ageDays(),
                line.lineCount());
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

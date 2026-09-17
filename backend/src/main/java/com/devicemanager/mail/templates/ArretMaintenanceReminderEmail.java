package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappel admin — arrêts maintenance ouverts trop longtemps.
 */
public final class ArretMaintenanceReminderEmail {

    private ArretMaintenanceReminderEmail() {
    }

    public record ArretLine(
            Long arretId,
            String atelierName,
            String masNumero,
            String motif,
            String dateHeureArret,
            long ageDays) {
    }

    public record Context(String periodKey, int minAgeDays, List<ArretLine> lines) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        int count = ctx.lines().size();
        String subject = "Arrêts maintenance — " + count + " ouvert(s) ≥ " + ctx.minAgeDays() + " j";

        List<String> textLines = new ArrayList<>();
        for (ArretLine line : ctx.lines()) {
            textLines.add(formatLineText(line));
        }
        String linesBlock = textLines.isEmpty()
                ? "(aucun)"
                : String.join("\n", textLines.stream().map(l -> "- " + l).toList());

        String text = """
                Bonjour Administrateur,

                DeviceManager signale des arrêts maintenance encore ouverts
                depuis au moins %d jour(s).

                Période : %s
                Arrêts concernés : %d

                Détail :
                %s

                Connectez-vous à DeviceManager pour reprendre ou suivre ces MAS.

                — DeviceManager
                """.formatted(ctx.minAgeDays(), ctx.periodKey(), count, linesBlock);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour Administrateur,</p>
                <p style="margin:0 0 12px;">DeviceManager signale des arrêts maintenance encore ouverts
                depuis au moins <strong>%d</strong> jour(s).</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Période</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Arrêts</td><td><strong>%d</strong></td></tr>
                </table>
                <p style="margin:0 0 6px;font-weight:600;">Détail</p>
                %s
                <p style="margin:16px 0 0;">Connectez-vous à DeviceManager pour reprendre ou suivre ces MAS.</p>
                """.formatted(
                ctx.minAgeDays(),
                EmailHtml.escapeHtml(ctx.periodKey()),
                count,
                EmailHtml.bulletList(textLines));

        String html = EmailHtml.emailShell("Arrêts maintenance ouverts", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(ArretLine line) {
        return "#%s — %s — MAS %s — depuis %s (%d j) — %s".formatted(
                line.arretId() == null ? "?" : line.arretId(),
                nullToDash(line.atelierName()),
                nullToDash(line.masNumero()),
                nullToDash(line.dateHeureArret()),
                line.ageDays(),
                nullToDash(line.motif()));
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

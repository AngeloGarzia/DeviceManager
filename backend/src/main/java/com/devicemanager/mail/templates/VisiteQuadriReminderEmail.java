package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappel admin — visites quadritrimestrielles WARN / OVERDUE.
 */
public final class VisiteQuadriReminderEmail {

    private VisiteQuadriReminderEmail() {
    }

    public record ObligationLine(
            String atelierName,
            String sfmNom,
            String marqueLabel,
            String level,
            long daysRemaining,
            String dueDate) {
    }

    public record Context(String periodKey, int warnDays, List<ObligationLine> lines) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        long overdue = ctx.lines().stream().filter(l -> "OVERDUE".equals(l.level())).count();
        long warn = ctx.lines().stream().filter(l -> "WARN".equals(l.level())).count();
        String subject = "Visites quadri — " + overdue + " en retard, " + warn + " à échéance";

        List<String> textLines = new ArrayList<>();
        for (ObligationLine line : ctx.lines()) {
            textLines.add(formatLineText(line));
        }
        String linesBlock = textLines.isEmpty()
                ? "(aucune)"
                : String.join("\n", textLines.stream().map(l -> "- " + l).toList());

        String text = """
                Bonjour Administrateur,

                DeviceManager signale des visites quadritrimestrielles SFM × marque à traiter
                (seuil d'alerte : %d jour(s) avant échéance).

                Période : %s
                En retard : %d
                À échéance (≤ %d j) : %d

                Détail :
                %s

                Connectez-vous à DeviceManager pour planifier ou enregistrer les visites.

                — DeviceManager
                """.formatted(
                ctx.warnDays(),
                ctx.periodKey(),
                overdue,
                ctx.warnDays(),
                warn,
                linesBlock);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour Administrateur,</p>
                <p style="margin:0 0 12px;">DeviceManager signale des visites quadritrimestrielles
                SFM × marque à traiter (seuil d'alerte : <strong>%d</strong> jour(s) avant échéance).</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Période</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">En retard</td><td><strong>%d</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">À échéance (≤ %d j)</td><td><strong>%d</strong></td></tr>
                </table>
                <p style="margin:0 0 6px;font-weight:600;">Détail</p>
                %s
                <p style="margin:16px 0 0;">Connectez-vous à DeviceManager pour planifier ou enregistrer les visites.</p>
                """.formatted(
                ctx.warnDays(),
                EmailHtml.escapeHtml(ctx.periodKey()),
                overdue,
                ctx.warnDays(),
                warn,
                EmailHtml.bulletList(textLines));

        String html = EmailHtml.emailShell("Visites quadritrimestrielles", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(ObligationLine line) {
        String levelLabel = "OVERDUE".equals(line.level()) ? "EN RETARD" : "À ÉCHÉANCE";
        return "[%s] %s — %s / %s — échéance %s (%s j)".formatted(
                levelLabel,
                nullToDash(line.atelierName()),
                nullToDash(line.sfmNom()),
                nullToDash(line.marqueLabel()),
                nullToDash(line.dueDate()),
                line.daysRemaining());
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappel admin — tâches À faire en retard.
 */
public final class TodoOverdueReminderEmail {

    private TodoOverdueReminderEmail() {
    }

    public record TodoLine(
            Long todoId,
            String atelierName,
            String titre,
            String severite,
            String dueAt,
            long overdueDays,
            String masNumero) {
    }

    public record Context(String periodKey, List<TodoLine> lines) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        int count = ctx.lines().size();
        String subject = "Todos en retard — " + count + " tâche(s)";

        List<String> textLines = new ArrayList<>();
        for (TodoLine line : ctx.lines()) {
            textLines.add(formatLineText(line));
        }
        String linesBlock = textLines.isEmpty()
                ? "(aucune)"
                : String.join("\n", textLines.stream().map(l -> "- " + l).toList());

        String text = """
                Bonjour Administrateur,

                DeviceManager signale des tâches « À faire » dont l'échéance est dépassée.

                Période : %s
                Tâches concernées : %d

                Détail :
                %s

                Connectez-vous à DeviceManager pour clôturer ou réaffecter ces tâches.

                — DeviceManager
                """.formatted(ctx.periodKey(), count, linesBlock);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour Administrateur,</p>
                <p style="margin:0 0 12px;">DeviceManager signale des tâches « À faire » dont l'échéance est dépassée.</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Période</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Tâches</td><td><strong>%d</strong></td></tr>
                </table>
                <p style="margin:0 0 6px;font-weight:600;">Détail</p>
                %s
                <p style="margin:16px 0 0;">Connectez-vous à DeviceManager pour clôturer ou réaffecter ces tâches.</p>
                """.formatted(
                EmailHtml.escapeHtml(ctx.periodKey()),
                count,
                EmailHtml.bulletList(textLines));

        String html = EmailHtml.emailShell("Todos en retard", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(TodoLine line) {
        String mas = line.masNumero() == null || line.masNumero().isBlank()
                ? ""
                : " — MAS " + line.masNumero();
        return "#%s — %s — [%s] %s — échéance %s (retard %d j)%s".formatted(
                line.todoId() == null ? "?" : line.todoId(),
                nullToDash(line.atelierName()),
                nullToDash(line.severite()),
                nullToDash(line.titre()),
                nullToDash(line.dueAt()),
                line.overdueDays(),
                mas);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

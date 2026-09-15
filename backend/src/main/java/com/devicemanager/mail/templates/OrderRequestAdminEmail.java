package com.devicemanager.mail.templates;

import com.devicemanager.mail.RenderedEmail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notification admin — nouvelle demande de commande à valider.
 */
public final class OrderRequestAdminEmail {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private OrderRequestAdminEmail() {
    }

    public record LineItem(String name, String reference, int quantity, String sfmLabel) {
    }

    public record Context(
            Long orderId,
            String atelierName,
            String technicienName,
            LocalDateTime dateDemande,
            List<LineItem> lines,
            List<String> sfmLines,
            String message) {
        public Context {
            lines = lines == null ? List.of() : List.copyOf(lines);
            sfmLines = sfmLines == null ? List.of() : List.copyOf(sfmLines);
        }
    }

    public static RenderedEmail render(Context ctx) {
        int pieceCount = ctx.lines() == null ? 0 : ctx.lines().size();
        String orderLabel = ctx.orderId() == null
                ? "(sera attribuée à l'envoi)"
                : String.valueOf(ctx.orderId());
        String subject = ctx.orderId() == null
                ? "Demande de commande (aperçu) — " + pieceCount + " pièce(s)"
                : "Demande de commande #" + ctx.orderId() + " — " + pieceCount + " pièce(s)";

        List<String> lineTexts = ctx.lines() == null ? List.of() : ctx.lines().stream()
                .map(OrderRequestAdminEmail::formatLineText)
                .toList();
        String linesBlock = lineTexts.isEmpty()
                ? "(aucune pièce)"
                : String.join("\n", lineTexts.stream().map(l -> "- " + l).toList());

        String sfmBlock = ctx.sfmLines() == null || ctx.sfmLines().isEmpty()
                ? "(aucun SFM associé aux pièces)"
                : ctx.sfmLines().stream().map(s -> "- " + s).collect(Collectors.joining("\n"));

        String dateStr = ctx.dateDemande() == null ? "—" : ctx.dateDemande().format(DATE_FMT);
        String message = ctx.message() == null || ctx.message().isBlank() ? "—" : ctx.message().trim();

        String text = """
                Bonjour Administrateur,

                Une nouvelle demande de commande nécessite votre validation dans DeviceManager.

                Demande n°%s
                Atelier : %s
                Technicien : %s
                Date/heure : %s

                Pièces détachées :
                %s
                SFM concernés :
                %s

                Message du technicien :
                %s

                Connectez-vous à l'application puis validez la demande pour envoyer la commande aux SFM.

                — DeviceManager
                """.formatted(
                orderLabel,
                nullToDash(ctx.atelierName()),
                nullToDash(ctx.technicienName()),
                dateStr,
                linesBlock,
                sfmBlock,
                message);

        String bodyHtml = """
                <p style="margin:0 0 12px;">Bonjour Administrateur,</p>
                <p style="margin:0 0 12px;">Une nouvelle demande de commande nécessite votre validation dans DeviceManager.</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 16px;font-size:14px;">
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Demande n°</td><td><strong>%s</strong></td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Atelier</td><td>%s</td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Technicien</td><td>%s</td></tr>
                  <tr><td style="padding:2px 12px 2px 0;color:#6b7280;">Date/heure</td><td>%s</td></tr>
                </table>
                <p style="margin:0 0 6px;font-weight:600;">Pièces détachées</p>
                %s
                <p style="margin:16px 0 6px;font-weight:600;">SFM concernés</p>
                %s
                <p style="margin:16px 0 6px;font-weight:600;">Message du technicien</p>
                %s
                <p style="margin:16px 0 0;">Connectez-vous à l'application puis validez la demande pour envoyer la commande aux SFM.</p>
                """.formatted(
                EmailHtml.escapeHtml(orderLabel),
                EmailHtml.escapeHtml(nullToDash(ctx.atelierName())),
                EmailHtml.escapeHtml(nullToDash(ctx.technicienName())),
                EmailHtml.escapeHtml(dateStr),
                EmailHtml.bulletList(lineTexts),
                EmailHtml.bulletList(ctx.sfmLines()),
                EmailHtml.textToHtmlParagraphs(message));

        String html = EmailHtml.emailShell("Nouvelle demande de commande", bodyHtml, null, null);
        return new RenderedEmail(subject, text, html);
    }

    private static String formatLineText(LineItem line) {
        StringBuilder sb = new StringBuilder(line.name());
        if (line.reference() != null && !line.reference().isBlank()) {
            sb.append(" (réf. ").append(line.reference()).append(")");
        }
        sb.append(" × ").append(line.quantity());
        if (line.sfmLabel() != null && !line.sfmLabel().isBlank()) {
            sb.append(" — SFM: ").append(line.sfmLabel());
        } else {
            sb.append(" — SFM: (non renseigné)");
        }
        return sb.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

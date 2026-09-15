package com.devicemanager.mail.templates;

import java.util.List;

/**
 * Helpers HTML pour les e-mails transactionnels (inline CSS, table layout).
 */
public final class EmailHtml {

    private EmailHtml() {
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Enveloppe HTML responsive basique (compatible clients mail).
     */
    public static String emailShell(String title, String bodyHtml, String ctaLabel, String ctaUrl) {
        String safeTitle = escapeHtml(title);
        String ctaBlock = "";
        if (ctaLabel != null && !ctaLabel.isBlank() && ctaUrl != null && !ctaUrl.isBlank()) {
            ctaBlock = """
                    <tr>
                      <td style="padding:24px 32px 8px;">
                        <a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;">%s</a>
                      </td>
                    </tr>
                    """.formatted(escapeHtml(ctaUrl), escapeHtml(ctaLabel));
        }
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f4f6;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0"
                          style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                          <tr>
                            <td style="background:#111827;color:#ffffff;padding:20px 32px;font-size:18px;font-weight:700;">
                              DeviceManager
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 32px 8px;font-size:20px;font-weight:600;color:#111827;">%s</td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 24px;font-size:15px;line-height:1.6;color:#374151;">%s</td>
                          </tr>
                          %s
                          <tr>
                            <td style="padding:16px 32px 24px;font-size:12px;color:#9ca3af;border-top:1px solid #e5e7eb;">
                              Message automatique — ne pas répondre directement à cet e-mail.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeTitle, bodyHtml, ctaBlock);
    }

    /** Convertit du texte multiligne en paragraphes HTML. */
    public static String textToHtmlParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return "<p></p>";
        }
        String[] blocks = text.split("\\n\\n");
        StringBuilder html = new StringBuilder();
        for (String block : blocks) {
            html.append("<p style=\"margin:0 0 12px;\">");
            String[] lines = block.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    html.append("<br/>");
                }
                html.append(escapeHtml(lines[i]));
            }
            html.append("</p>");
        }
        return html.toString();
    }

    public static String bulletList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "<p style=\"margin:0 0 12px;color:#6b7280;\">(aucun)</p>";
        }
        StringBuilder html = new StringBuilder("<ul style=\"margin:0 0 12px;padding-left:20px;\">");
        for (String item : items) {
            html.append("<li style=\"margin:0 0 6px;\">").append(escapeHtml(item)).append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }
}

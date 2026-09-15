package com.devicemanager.mail;

/**
 * Résultat d'un envoi SMTP — ne jamais throw depuis {@link EmailService}.
 */
public record EmailSendResult(boolean ok, boolean skipped, String error) {

    public static EmailSendResult success() {
        return new EmailSendResult(true, false, null);
    }

    public static EmailSendResult simulated() {
        return new EmailSendResult(true, true, null);
    }

    public static EmailSendResult failure(String error) {
        return new EmailSendResult(false, false, error);
    }
}

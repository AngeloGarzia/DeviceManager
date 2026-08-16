package com.devicemanager.security;

/**
 * Statuts possibles d'une demande de commande ({@code commande.status}).
 */
public final class OrderStatuses {

    /** Demande créée — en attente de validation par un administrateur. */
    public static final String PENDING = "PENDING";

    /** Validée par un admin — courriels de notification envoyés aux contacts SFM. */
    public static final String VALIDATED = "VALIDATED";

    /** Réception confirmée par un admin — stock des pièces mis à jour. */
    public static final String RECEIVED = "RECEIVED";

    /** Ancien statut legacy, équivalent à {@link #PENDING}. */
    public static final String SENT = "SENT";

    private OrderStatuses() {
    }

    /**
     * Indique si la demande est encore en attente de validation.
     *
     * @param status statut brut en base
     * @return {@code true} pour {@link #PENDING} ou {@link #SENT}
     */
    public static boolean isPending(String status) {
        return PENDING.equals(status) || SENT.equals(status);
    }

    /**
     * Indique si la demande est validée et en attente de réception physique.
     */
    public static boolean isValidated(String status) {
        return VALIDATED.equals(status);
    }

    /**
     * Indique si la réception a déjà été confirmée.
     */
    public static boolean isReceived(String status) {
        return RECEIVED.equals(status);
    }

    /**
     * Les lignes peuvent être ajustées tant que la réception n'est pas confirmée.
     */
    public static boolean canEditLines(String status) {
        return isPending(status) || isValidated(status);
    }

    /**
     * Un devis (PDF ou image) peut être associé une fois la demande validée (y compris après réception).
     */
    public static boolean canAttachDevis(String status) {
        return isValidated(status) || isReceived(status);
    }
}

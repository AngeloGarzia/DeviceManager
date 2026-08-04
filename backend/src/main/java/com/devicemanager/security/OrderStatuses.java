package com.devicemanager.security;

/**
 * Statuts possibles d'une demande de commande ({@code commande.status}).
 */
public final class OrderStatuses {

    /** Demande créée — en attente de validation par un administrateur. */
    public static final String PENDING = "PENDING";

    /** Validée par un admin — courriels de notification envoyés aux contacts SFM. */
    public static final String VALIDATED = "VALIDATED";

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
}

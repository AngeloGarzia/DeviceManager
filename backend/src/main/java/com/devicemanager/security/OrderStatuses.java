package com.devicemanager.security;

public final class OrderStatuses {

    /** Demande créée — en attente de validation admin. */
    public static final String PENDING = "PENDING";

    /** Validée par un admin — mails envoyés aux SFM. */
    public static final String VALIDATED = "VALIDATED";

    /** Ancien statut legacy (traité comme PENDING). */
    public static final String SENT = "SENT";

    private OrderStatuses() {
    }

    public static boolean isPending(String status) {
        return PENDING.equals(status) || SENT.equals(status);
    }
}

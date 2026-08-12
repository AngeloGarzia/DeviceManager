package com.devicemanager.security;

/**
 * Types d'événements exposés par {@code GET /api/timeline}.
 */
public final class TimelineEventTypes {

    public static final String ORDER_REQUEST = "ORDER_REQUEST";
    public static final String ORDER_VALIDATED = "ORDER_VALIDATED";
    public static final String ORDER_RECEIVED = "ORDER_RECEIVED";
    public static final String INTERVENTION = "INTERVENTION";
    public static final String STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";

    private TimelineEventTypes() {
    }
}

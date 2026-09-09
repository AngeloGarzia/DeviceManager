package com.devicemanager.security;

/**
 * Types d'événements exposés par {@code GET /api/timeline}.
 */
public final class TimelineEventTypes {

    public static final String ORDER_REQUEST = "ORDER_REQUEST";
    public static final String ORDER_VALIDATED = "ORDER_VALIDATED";
    public static final String ORDER_RECEIVED = "ORDER_RECEIVED";
    /** Bon d'intervention (consommation de pièces). */
    public static final String INTERVENTION = "INTERVENTION";
    /** Intervention technique libre (table interventions). */
    public static final String INTERVENTION_TECHNIQUE = "INTERVENTION_TECHNIQUE";
    /** Ligne FIT signée. */
    public static final String FIT = "FIT";
    public static final String STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";
    /** Tâche « À faire » rattachée à une MAS. */
    public static final String TODO_TACHE = "TODO_TACHE";

    /** Colonnes d'abscisse (swimlane). */
    public static final String COL_COMMANDES = "COMMANDES";
    public static final String COL_BONS = "BONS";
    public static final String COL_INTERVENTIONS = "INTERVENTIONS";
    public static final String COL_FIT = "FIT";
    public static final String COL_STOCK = "STOCK";
    public static final String COL_TODOS = "TODOS";

    private TimelineEventTypes() {
    }

    public static String columnFor(String type) {
        if (type == null) {
            return COL_STOCK;
        }
        return switch (type) {
            case ORDER_REQUEST, ORDER_VALIDATED, ORDER_RECEIVED -> COL_COMMANDES;
            case INTERVENTION -> COL_BONS;
            case INTERVENTION_TECHNIQUE -> COL_INTERVENTIONS;
            case FIT -> COL_FIT;
            case TODO_TACHE -> COL_TODOS;
            case STOCK_ADJUSTMENT -> COL_STOCK;
            default -> COL_STOCK;
        };
    }
}

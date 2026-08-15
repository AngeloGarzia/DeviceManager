package com.devicemanager.entity;

/**
 * Statut d'exploitation d'une machine à sous.
 */
public enum MasStatut {
    UTILISEE,
    EN_RESERVE,
    VENDUE,
    DETRUITE;

    public boolean isUtilisee() {
        return this == UTILISEE;
    }

    public String label() {
        return switch (this) {
            case UTILISEE -> "Machine utilisée";
            case EN_RESERVE -> "En réserve";
            case VENDUE -> "Vendue";
            case DETRUITE -> "Détruite";
        };
    }

    public static MasStatut fromUtilise(boolean utilise) {
        return utilise ? UTILISEE : EN_RESERVE;
    }

    public static MasStatut parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UTILISEE;
        }
        try {
            return MasStatut.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UTILISEE;
        }
    }
}

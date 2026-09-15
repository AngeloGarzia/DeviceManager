package com.devicemanager.security;

/**
 * Rôles applicatifs et préfixes Spring Security pour DeviceManager.
 * <p>
 * Les constantes {@code ROLE_*} sont utilisées dans les autorités JWT ;
 * les constantes sans préfixe correspondent aux valeurs stockées en base.
 */
public final class Roles {

    /** Administrateur : gestion métier et organisation (comptes, ateliers, commandes). */
    public static final String ADMIN = "ADMIN";

    /**
     * Super-administrateur : droits administrateur + paramètres applicatifs (Setup API) et logs.
     */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Technicien : accès métier limité à son atelier préféré. */
    public static final String TECHNICIEN = "TECHNICIEN";

    /** Autorité Spring Security pour {@link #ADMIN}. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** Autorité Spring Security pour {@link #SUPER_ADMIN}. */
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    /** Autorité Spring Security pour {@link #TECHNICIEN}. */
    public static final String ROLE_TECHNICIEN = "ROLE_TECHNICIEN";

    private Roles() {}

    /** Administrateur ou super-administrateur. */
    public static boolean isAdminLike(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }

    /** Super-administrateur uniquement. */
    public static boolean isSuperAdmin(String role) {
        return SUPER_ADMIN.equals(role);
    }

    /** Technicien (y compris alias historique {@code TECH}). */
    public static boolean isTechnicien(String role) {
        return TECHNICIEN.equals(role) || "TECH".equals(role);
    }
}

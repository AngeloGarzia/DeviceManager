package com.devicemanager.security;

/**
 * Rôles applicatifs et préfixes Spring Security pour DeviceManager.
 * <p>
 * Les constantes {@code ROLE_*} sont utilisées dans les autorités JWT ;
 * les constantes sans préfixe correspondent aux valeurs stockées en base.
 */
public final class Roles {

    /** Administrateur : accès complet, gestion des utilisateurs et du setup. */
    public static final String ADMIN = "ADMIN";

    /** Technicien : accès métier limité à son atelier préféré. */
    public static final String TECHNICIEN = "TECHNICIEN";

    /** Autorité Spring Security pour {@link #ADMIN}. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** Autorité Spring Security pour {@link #TECHNICIEN}. */
    public static final String ROLE_TECHNICIEN = "ROLE_TECHNICIEN";

    private Roles() {}
}

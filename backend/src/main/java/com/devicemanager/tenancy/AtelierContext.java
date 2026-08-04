package com.devicemanager.tenancy;

/**
 * Contexte d'atelier actif pour la requête HTTP courante, stocké dans un {@link ThreadLocal}.
 * <p>
 * Renseigné par {@link AtelierContextFilter} à partir de l'en-tête {@code X-Atelier-Id}
 * et lu par les services pour filtrer les données par atelier.
 */
public final class AtelierContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private AtelierContext() {
    }

    /**
     * Fixe l'identifiant d'atelier pour le thread courant.
     *
     * @param atelierId identifiant de l'atelier sélectionné
     */
    public static void set(Long atelierId) {
        CURRENT.set(atelierId);
    }

    /**
     * Retourne l'identifiant d'atelier courant, ou {@code null} si aucun n'a été sélectionné.
     *
     * @return identifiant d'atelier, ou {@code null}
     */
    public static Long get() {
        return CURRENT.get();
    }

    /**
     * Retourne l'identifiant d'atelier courant ou lève une exception si absent.
     *
     * @return identifiant d'atelier non nul
     * @throws IllegalStateException si aucun atelier n'est sélectionné pour la requête
     */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("Aucun atelier sélectionné");
        }
        return id;
    }

    /** Supprime le contexte d'atelier du thread courant (appelé en fin de requête). */
    public static void clear() {
        CURRENT.remove();
    }
}

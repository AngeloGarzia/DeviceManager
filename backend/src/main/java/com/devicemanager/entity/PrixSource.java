package com.devicemanager.entity;

/**
 * Source d'une observation de prix.
 */
public enum PrixSource {
    /** Prix confirmé depuis un devis de commande. */
    DEVIS,
    /** Prix saisi manuellement (ex. à la création de la pièce). */
    SAISIE
}

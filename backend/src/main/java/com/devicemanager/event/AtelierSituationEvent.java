package com.devicemanager.event;

/**
 * Événement métier d'atelier destiné à actualiser la mémoire synaptique IA.
 *
 * @param atelierId identifiant atelier
 * @param type      code court (ex. DEVICE_CREATED, TODO_DONE)
 * @param fact      phrase factuelle en français
 */
public record AtelierSituationEvent(Long atelierId, String type, String fact) {
}

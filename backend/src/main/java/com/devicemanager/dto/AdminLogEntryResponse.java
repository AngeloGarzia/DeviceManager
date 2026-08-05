package com.devicemanager.dto;

import java.time.Instant;

/**
 * Entrée de journal exposée aux administrateurs.
 *
 * @param id        identifiant séquentiel
 * @param timestamp instant de l'événement
 * @param level     niveau SLF4J (INFO, WARN, ERROR…)
 * @param logger    nom du logger
 * @param thread    fil d'exécution
 * @param message   message formaté
 * @param throwable stack trace éventuelle
 */
public record AdminLogEntryResponse(
        long id,
        Instant timestamp,
        String level,
        String logger,
        String thread,
        String message,
        String throwable
) {
}

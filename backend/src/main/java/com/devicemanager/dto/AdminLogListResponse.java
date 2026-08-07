package com.devicemanager.dto;

import java.util.List;

/**
 * Réponse filtrée des logs admin persistés en base.
 *
 * @param totalCount   nombre total de lignes en table {@code app_log}
 * @param retentionMax rétention configurée
 * @param returned     nombre d'entrées dans {@code items}
 * @param items        événements (plus récent d'abord)
 */
public record AdminLogListResponse(
        int totalCount,
        int retentionMax,
        int returned,
        List<AdminLogEntryResponse> items
) {
    /** Copie défensive : la liste exposée reste immuable. */
    public AdminLogListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

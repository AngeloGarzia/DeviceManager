package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agrégat des tâches « À faire » de l'atelier.
 */
@Data
@Builder
public class TodoListResponse {
    private long count;
    /** Occurrences échues non clôturées (warning). */
    private long overdueCount;
    private List<TodoTacheResponse> items;
}

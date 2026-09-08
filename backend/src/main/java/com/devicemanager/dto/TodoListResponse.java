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
    private List<TodoTacheResponse> items;
}

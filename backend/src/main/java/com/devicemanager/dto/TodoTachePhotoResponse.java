package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Métadonnées d'une photo attachée à une tâche simple.
 */
@Data
@Builder
public class TodoTachePhotoResponse {
    private Long id;
    private String photoUrl;
    private String contentType;
    private Long fileSize;
    private int position;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Métadonnées d'une photo attachée à une pièce.
 */
@Data
@Builder
public class DevicePhotoResponse {
    private Long id;
    private String photoUrl;
    private String contentType;
    private Long fileSize;
    /** Position dans la galerie (0 = photo principale). */
    private int position;
}

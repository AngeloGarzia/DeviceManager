package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Document PDF d'une pièce (manuel / datasheet / notice).
 */
@Data
@Builder
public class DeviceDocumentResponse {
    private Long id;
    /** MANUAL | DATASHEET | NOTICE */
    private String docType;
    private String fileUrl;
    private String originalName;
    private String contentType;
    private Long fileSize;
}

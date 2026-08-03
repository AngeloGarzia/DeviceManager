package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DevicePhotoResponse {
    private Long id;
    private String photoUrl;
    private String contentType;
    private Long fileSize;
    private int position;
}

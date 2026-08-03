package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderRequestLineResponse {
    private Long id;
    private Long deviceId;
    private String pieceNom;
    private String reference;
    private Integer quantite;
    private String photoUrl;
    private Long sfmId;
    private String sfmNom;
}

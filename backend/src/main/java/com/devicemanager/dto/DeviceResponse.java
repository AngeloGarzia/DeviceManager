package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DeviceResponse {
    private Long id;
    private String nom;
    private String reference;
    private String usage;
    private LocalDate dateAcquisition;
    private boolean obsolete;
    /** Photo principale (compat listes / commandes). */
    private String photoUrl;
    private String contentType;
    private Long fileSize;
    private List<DevicePhotoResponse> photos;
    private Long sfmId;
    private String sfmNom;
    private Long masId;
    private String masNumero;
    private String masMarque;
    private Long marqueId;
    private String marque;
    private String marqueLabel;
}

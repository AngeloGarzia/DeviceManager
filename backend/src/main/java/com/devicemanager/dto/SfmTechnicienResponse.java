package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Technicien SFM (contact externe) pour réutilisation multi-SFM.
 */
@Data
@Builder
public class SfmTechnicienResponse {
    private Long id;
    private String nom;
    private String telephone;
    private String email;
    private boolean receiveOrderMails;
    @Builder.Default
    private List<Long> sfmIds = new ArrayList<>();
    @Builder.Default
    private List<String> sfmNoms = new ArrayList<>();
}

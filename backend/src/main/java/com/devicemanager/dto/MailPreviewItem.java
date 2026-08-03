package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailPreviewItem {
    /** ADMIN ou SFM */
    private String kind;
    private String to;
    private String subject;
    private String body;
    private String sfmNom;
}

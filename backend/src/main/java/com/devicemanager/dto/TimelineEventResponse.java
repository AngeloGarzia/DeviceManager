package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Événement unifié pour la timeline Commandes.
 */
@Data
@Builder
public class TimelineEventResponse {
    private String type;
    private LocalDateTime at;
    private String title;
    private String subtitle;
    private String acteur;
    private String refType;
    private Long refId;
    private Integer deltaStock;
    private List<TimelineLineDto> lignes;
}

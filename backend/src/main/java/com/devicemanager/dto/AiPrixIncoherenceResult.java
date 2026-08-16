package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiPrixIncoherenceResult {
    private Long deviceId;
    private String severity;
    private List<String> reasons;
    private String suggestedAction;
    private String summary;
}

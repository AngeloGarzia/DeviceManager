package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminPurgeResultResponse {

    private String rootType;
    private Long rootId;
    private List<AdminPurgePlanNode> applied;
    private String message;
}

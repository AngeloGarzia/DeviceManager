package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Plan de purge Super Admin avant confirmation.
 */
@Data
@Builder
public class AdminPurgePlanResponse {

    private String rootType;
    private Long rootId;
    private String rootLabel;
    private List<AdminPurgePlanNode> nodes;
    private List<String> warnings;
    private List<String> blockers;
    private boolean executable;
    /** Phrase à retaper pour confirmer, ex. SUPPRIMER DEVICE #12 */
    private String confirmPhrase;
}

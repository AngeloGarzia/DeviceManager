package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Nœud du plan de purge (prévisualisation).
 */
@Data
@Builder
public class AdminPurgePlanNode {

    private String entityType;
    private Long entityId;
    private String label;
    /** DELETE | ARCHIVE | DETACH | SKIP */
    private String action;
    private String detail;
}

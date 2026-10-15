package com.devicemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Exécution d'une purge en chaîne (Super Admin).
 */
@Data
public class AdminPurgeExecuteRequest {

    @NotBlank
    private String rootType;

    @NotNull
    private Long rootId;

    /** Doit égaler la phrase renvoyée par le preview. */
    @NotBlank
    private String confirmPhrase;
}

package com.devicemanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Listes d'admins et techniciens du groupe pour les combos de signature FIT.
 */
@Data
@Builder
public class FitSignatairesResponse {

    @Builder.Default
    private List<FitSignataireDto> admins = new ArrayList<>();

    @Builder.Default
    private List<FitSignataireDto> techniciens = new ArrayList<>();
}

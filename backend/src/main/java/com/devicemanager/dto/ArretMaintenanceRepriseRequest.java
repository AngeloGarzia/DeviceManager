package com.devicemanager.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArretMaintenanceRepriseRequest {

    private LocalDateTime dateHeureReprise;

    /** Obligatoire si l'arrêt exigeait un registre technique à jour. */
    private String signatureRedemarrage;

    @Size(max = 120)
    private String signataireRedemarrageNom;
}

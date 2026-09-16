package com.devicemanager.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Création / mise à jour d'une règle de tâche récurrente.
 */
@Data
public class TodoRecurrenceRequest {

    @NotBlank
    @Size(max = 200)
    private String titre;

    @Size(max = 2000)
    private String description;

    /** HIGH / MEDIUM / LOW. */
    private String severite;

    private Long masId;

    /** DAILY / WEEKLY / MONTHLY / INTERVAL_DAYS. */
    @NotBlank
    private String frequence;

    @Min(1)
    @Max(365)
    private Integer intervalDays;

    /** 1 = lundi … 7 = dimanche. */
    @Min(1)
    @Max(7)
    private Integer jourSemaine;

    @Min(1)
    @Max(28)
    private Integer jourMois;

    private LocalTime heureDue;

    @NotNull
    private LocalDate dateDebut;

    private LocalDate dateFin;

    private Boolean active;
}
